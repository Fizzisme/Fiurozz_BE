import {BadRequestException, Injectable, UnauthorizedException} from '@nestjs/common';
import {RegisterDto} from "./dto/register.dto.js";
import {PrismaService} from "../prisma/prisma.service.js";
import {LoginDto} from "./dto/login.dto.js";
import {PasswordService} from "./password/password.service.js";
import {RefreshTokenService} from "./token/refresh-token.service.js";
import {JwtTokenService} from "./jwt/jwt-token.service.js";
import type { Request, Response } from 'express';

@Injectable()
export class AuthService {
    constructor(
        private readonly prismaService: PrismaService,
        private readonly passwordService: PasswordService,
        private readonly refreshTokenService: RefreshTokenService,
        private readonly jwtTokenService: JwtTokenService,
    ) {}
    async register (data: RegisterDto) {

        const existed = await this.prismaService.user.findUnique({
            where: {
                email: data.email,
            }
        })

        if (existed) {
            throw new BadRequestException('Email already exists.');
        }

        const passwordHash = await this.passwordService.hashPassword(data.password);

        await this.prismaService.user.create({
            data: {
                email: data.email,
                fullName: data.fullName,
                displayName: data.displayName,
                passwordHash: passwordHash,
            },
        })

         return {
             message: 'Register successfully.',
         };
    }

    async login(data: LoginDto, res: Response) {

        const user = await this.prismaService.user.findUnique({
            where: {
                email: data.email,
            }
        })

        if (!user) {
            throw new UnauthorizedException('Invalid email or password.');
        }

        if(!user.passwordHash){
            throw new UnauthorizedException('This account uses Google/GitHub login.');
        }

        if(!await this.passwordService.comparePassword(data.password,user.passwordHash)){
            throw new UnauthorizedException('Invalid email or password.');
        }

        const tokens = await this.jwtTokenService.generateTokens(user);


        await this.refreshTokenService.save(
            user.id,
            tokens.jti,
            tokens.refreshToken,
            data.deviceName,
            data.userAgent,
            data.ipAddress,

        );

        res.cookie("refreshToken", tokens.refreshToken, {
            httpOnly: true,
            secure: process.env.NODE_ENV === "production",
            sameSite: "strict",
            path: "/api/auth/refresh",
            maxAge: 7 * 24 * 60 * 60 * 1000,
        })

        return {
            message: 'Login successfully.',
            ...tokens,
        };
    }

    async refresh(req: Request, res: Response) {
        const refreshToken = req.cookies.refreshToken;
        if (!refreshToken) {
            throw new UnauthorizedException();
        }

        const payload = await this.jwtTokenService.verifyRefreshToken(refreshToken);

        const token = await this.refreshTokenService.findByJti(payload.jti);
        if (!token || token.revoked || token.expiresAt < new Date()) {
            throw new UnauthorizedException();
        }

        const isMatch = await this.passwordService.comparePassword(refreshToken, token.tokenHash);
        if (!isMatch) {
            throw new UnauthorizedException();
        }

        const user = await this.prismaService.user.findUnique({
            where: { id: payload.sub },
        });
        if (!user) {
            throw new UnauthorizedException();
        }

        const tokens = await this.jwtTokenService.generateTokens(user);
        const newTokenHash = await this.passwordService.hashPassword(tokens.refreshToken);

        // Xoá token cũ + tạo token mới trong 1 transaction duy nhất
        await this.prismaService.$transaction([
            this.prismaService.refreshToken.delete({ where: { id: token.id } }),
            this.prismaService.refreshToken.create({
                data: {
                    userId: user.id,
                    jti: tokens.jti,
                    tokenHash: newTokenHash,
                    deviceName: token.deviceName,
                    userAgent: token.userAgent,
                    ipAddress: token.ipAddress,
                    expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000),
                },
            }),
        ]);

        res.cookie('refreshToken', tokens.refreshToken, {
            httpOnly: true,
            secure: process.env.NODE_ENV === 'production',
            sameSite: 'strict',
            path: '/api/auth/refresh',
            maxAge: 7 * 24 * 60 * 60 * 1000,
        });

        return { accessToken: tokens.accessToken };
    }

}
