import {Injectable} from "@nestjs/common";
import {JwtTokenService} from "../jwt/jwt-token.service.js";
import {RefreshTokenService} from "./refresh-token.service.js";
import type {Response} from "express";
import {DeviceInfo} from "../interfaces/device-info.interface.js";
import {IAccount} from "../../account/interfaces/account.interface.js";

// Shared token-issuance logic used by every login flow (password login,
// Google OAuth callback, etc.) so they all end up with identical
// token generation, session persistence, and cookie behavior.
@Injectable()
export class TokenService{
    constructor(
        private readonly jwtTokenService: JwtTokenService,
        private readonly refreshTokenService: RefreshTokenService,
    ){}
    async issueToken(account: IAccount, device: DeviceInfo, res: Response){
        const tokens = await this.jwtTokenService.generateTokens(account);

        // Persist the refresh token as a revocable session (see
        // RefreshTokenService), tied to this device/IP for the
        // "active sessions" list and later revocation.
        await this.refreshTokenService.save(
            account.id,
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
            path: "/api/auth",
            maxAge: 7 * 24 * 60 * 60 * 1000,
        })

        return {
            message: 'Login successfully.',
            data: {
                ...tokens,
            }
        };
    }
}