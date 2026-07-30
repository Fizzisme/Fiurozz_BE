import {Injectable} from "@nestjs/common";
import {JwtTokenService} from "../jwt/jwt-token.service.js";
import {RefreshTokenService} from "./refresh-token.service.js";
import type {Response} from "express";
import {DeviceInfo} from "../interfaces/device-info.interface.js";
import {IUser} from "../../user/interfaces/user.interface.js";

@Injectable()
export class TokenService{
    constructor(
        private readonly jwtTokenService: JwtTokenService,
        private readonly refreshTokenService: RefreshTokenService,
    ){}
    async issueToken(user: IUser, device: DeviceInfo, res: Response){
        const tokens = await this.jwtTokenService.generateTokens(user);

        await this.refreshTokenService.save(
            user.id,
            tokens.jti,
            tokens.refreshToken,
            device.deviceName,
            device.userAgent,
            device.ipAddress,
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
}