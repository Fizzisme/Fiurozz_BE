import {BadRequestException, Injectable, NotFoundException, UnauthorizedException} from '@nestjs/common';
import {RegisterDto} from "./dto/register.dto.js";
import {PrismaService} from "../prisma/prisma.service.js";
import {LoginDto} from "./dto/login.dto.js";
import {PasswordService} from "./password/password.service.js";
import {RefreshTokenService} from "./token/refresh-token.service.js";
import {JwtTokenService} from "./jwt/jwt-token.service.js";
import type { Request, Response } from 'express';

import {TokenService} from "./token/token.service.js";
import {IUser} from "../user/interfaces/user.interface.js";
import {UserService} from "../user/user.service.js";
import {OauthAccountService} from "../oauth-account/oauth-account.service.js";
import {IOAuthUser} from "../oauth-account/interfaces/oauth-user.interface.js";

@Injectable()
export class AuthService {
    constructor(
        private readonly userService: UserService,
        private readonly prismaService: PrismaService,
        private readonly passwordService: PasswordService,
        private readonly refreshTokenService: RefreshTokenService,
        private readonly jwtTokenService: JwtTokenService,
        private readonly oauthService: OauthAccountService,
        private readonly tokenService: TokenService,
    ) {}
    async register (data: RegisterDto) {

        const user : IUser | null = await this.userService.findUserByEmail(data.email);

        if (user) {
            throw new BadRequestException('Email already exists.');
        }

        const passwordHash = await this.passwordService.hashPassword(data.password);

        await this.userService.createUser(
            {
                email: data.email,
                fullName: data.fullName,
                displayName: data.displayName,
                passwordHash: passwordHash,
            }
        )

         return {
             message: 'Register successfully.',
         };
    }

    async login(data: LoginDto, req: Request, res: Response) {

        const user : IUser | null = await this.userService.findUserByEmail(data.email);

        if (!user) {
            throw new UnauthorizedException('Invalid email or password.');
        }

        if(!user.passwordHash){
            throw new UnauthorizedException('This account uses Google/GitHub login.');
        }

        if(!await this.passwordService.comparePassword(data.password,user.passwordHash)){
            throw new UnauthorizedException('Invalid email or password.');
        }

        return this.tokenService.issueToken(user,{
                deviceName: req.headers['x-device-name'] as string | undefined,
                userAgent: req.headers['user-agent'],
                ipAddress: req.ip,
            },
            res)
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

        const user: IUser | null = await this.userService.findUserById(payload.sub);
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
            path: '/api/auth',
            maxAge: 7 * 24 * 60 * 60 * 1000,
        });

        return { accessToken: tokens.accessToken };
    }

    async oauthLogin(req: Request, res: Response) {

        const profile = req.user as IOAuthUser;

        if (profile.provider === "github" && !profile.email) {
            throw new UnauthorizedException(
                "A verified email is required to sign in with GitHub.",
            );
        }

        const user: IUser = await this.oauthService.loginWithOauth(profile)

        return this.tokenService.issueToken(user,{
            deviceName: req.get("x-device-name"),
            userAgent: req.get("user-agent"),
            ipAddress: req.ip,
        },
        res)
    }

    async logout(
        req: Request,
        res: Response,
    ) {

        const refreshToken =
            req.cookies.refreshToken;

        if (!refreshToken) {
            return {
                message: "Logout successfully.",
            };
        }

        try {

            const payload =
                await this.jwtTokenService.verifyRefreshToken(
                    refreshToken,
                );

            await this.refreshTokenService.deleteByJti(
                payload.jti,
            );

        } catch {
            // Ignore invalid/expired refresh token.
        }

        res.clearCookie("refreshToken", {
            httpOnly: true,
            secure:
                process.env.NODE_ENV === "production",
            sameSite: "strict",
            path: "/api/auth",
        });

        return {
            message: "Logout successfully.",
        };
    }

    async getSessions(req: Request) {

        const userId = req.get("x-user-id");

        if (!userId) {
            throw new UnauthorizedException();
        }

        return this.refreshTokenService.getSessions(
            userId,
        );
    }

    async logoutSession(
        sessionId: string,
        req: Request,
    ) {

        const userId = req.get("x-user-id");

        if (!userId) {
            throw new UnauthorizedException();
        }

        const result = await this.refreshTokenService.deleteSession(
            sessionId,
            userId,
        );

        if (result.count === 0) {
            throw new NotFoundException(
                "Session not found.",
            );
        }

        return {
            message: "Session removed successfully.",
        };
    }

    async logoutAll(req: Request) {

        const userId = req.get("x-user-id");

        if (!userId) {
            throw new UnauthorizedException();
        }

        await this.refreshTokenService.deleteAllSessions(
            userId,
        );

        return {
            message: "Logged out from all devices.",
        };
    }
}
