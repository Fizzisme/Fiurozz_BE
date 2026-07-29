import {Injectable, UnauthorizedException} from "@nestjs/common";
import {JwtService} from "@nestjs/jwt";
import {randomUUID} from 'crypto'

@Injectable()
export class JwtTokenService {
    constructor(private readonly jwtService: JwtService) {}

    async generateTokens(user: {id: string; email: string; roles: string[]}) {
        const payload = {
            sub: user.id,
            email: user.email,
            roles: user.roles,
        }

        const jti = randomUUID()

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

    async verifyRefreshToken(refreshToken: string) {
        try {
            return await this.jwtService.verifyAsync(refreshToken, {
                secret: process.env.JWT_REFRESH_SECRET,
            });
        } catch {
            throw new UnauthorizedException("Refresh token không hợp lệ hoặc đã hết hạn.");
        }
    }
}