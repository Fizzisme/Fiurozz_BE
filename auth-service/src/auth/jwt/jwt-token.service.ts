import {Injectable} from "@nestjs/common";
import {JwtService} from "@nestjs/jwt";

@Injectable()
export class JwtTokenService {
    constructor(private readonly jwtService: JwtService) {}

    async generateTokens(user: {id: string; email: string; roles: string[]}) {
        const payload = {
            sub: user.id,
            email: user.email,
            roles: user.roles,
        }

        const [accessToken, refreshToken] =
            await Promise.all([
                this.jwtService.sign(payload, {
                    secret: process.env.JWT_ACCESS_SECRET,
                    expiresIn: process.env.JWT_ACCESS_EXPIRES as `${number}${"s"|"m"|"h"|"d"}`,
                }),

                this.jwtService.sign(payload, {
                    secret: process.env.JWT_REFRESH_SECRET,
                    expiresIn: process.env.JWT_REFRESH_EXPIRES as `${number}${"s"|"m"|"h"|"d"}`,
                })
            ])

        return {accessToken, refreshToken}

    }
}