import { Module } from '@nestjs/common';
import { AuthController } from './auth.controller.js';
import { AuthService } from './auth.service.js';
import {JwtModule} from "@nestjs/jwt";
import {JwtTokenService} from "./jwt/jwt-token.service.js";
import {PasswordService} from "./password/password.service.js";
import {RefreshTokenService} from "./token/refresh-token.service.js";
import {PassportModule} from "@nestjs/passport";
import {TokenService} from "./token/token.service.js";
import {UserModule} from "../user/user.module.js";
import {OauthAccountModule} from "../oauth-account/oauth-account.module.js";


@Module({
    imports: [
        JwtModule.register({}),
        UserModule,
        OauthAccountModule,
    ],
    controllers:[AuthController],
    providers: [
        AuthService,
        JwtTokenService,
        PasswordService,
        RefreshTokenService,
        TokenService,
    ]
})
export class AuthModule {}
