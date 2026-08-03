import {Injectable, UnauthorizedException} from "@nestjs/common";
import {JwtService} from "@nestjs/jwt";
import {uuidv7} from "uuidv7";

@Injectable()
export class JwtTokenService {
    constructor(private readonly jwtService: JwtService) {}

    // Issues an access token + refresh token pair. Refresh token gets a
    // unique jti (JWT ID), used to track/revoke it later (e.g. in
    // RefreshTokenService), independent of the token's own expiry.
    async generateTokens(user: {id: string; email: string; roles: string[]}) {
        const payload = {
            sub: user.id,
            email: user.email,
            roles: user.roles,
        }

        const jti = uuidv7()

        const [accessToken, refreshToken] =
            await Promise.all([
                this.jwtService.signAsync(payload, {
                    secret: process.env.JWT_ACCESS_SECRET,
                    expiresIn: process.env.JWT_ACCESS_EXPIRES as `${number}${"s"|"m"|"h"|"d"}`,
                    issuer: process.env.JWT_ISSUER,
                }),

                this.jwtService.signAsync(payload, {
                    secret: process.env.JWT_REFRESH_SECRET,
                    expiresIn: process.env.JWT_REFRESH_EXPIRES as `${number}${"s"|"m"|"h"|"d"}`,
                    issuer: process.env.JWT_ISSUER,
                    jwtid: jti,
                })
            ])

        return {accessToken, refreshToken, jti}

    }

    // Verifies a refresh token's signature/expiry only — does not check
    // revocation status (see RefreshTokenService for that).
    async verifyRefreshToken(refreshToken: string) {
        try {
            return await this.jwtService.verifyAsync(refreshToken, {
                secret: process.env.JWT_REFRESH_SECRET,
            });
        } catch {
            throw new UnauthorizedException("Invalid or expired refresh token.");
        }
    }
}