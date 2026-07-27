import {BadRequestException, Injectable, UnauthorizedException} from '@nestjs/common';
import {RegisterDto} from "./dto/register.dto.js";
import {PrismaService} from "../prisma/prisma.service.js";
import {LoginDto} from "./dto/login.dto.js";
import {PasswordService} from "./password/password.service.js";
import {RefreshTokenService} from "./token/refresh-token.service.js";
import {JwtTokenService} from "./jwt/jwt-token.service.js";


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

    async login(data: LoginDto) {

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
            tokens.refreshToken,
            data.deviceName,
            data.userAgent,
            data.ipAddress,

        );

        return {
            message: 'Login successfully.',
            ...tokens,
        };
    }
}
