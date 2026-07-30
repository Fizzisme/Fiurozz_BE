import {Module} from "@nestjs/common";
import {OauthAccountService} from "./oauth-account.service.js";
import {UserModule} from "../user/user.module.js";
import {GoogleStrategy} from "./strategy/google.strategy.js";
import {PassportModule} from "@nestjs/passport";
import {GithubStrategy} from "./strategy/github.strategy.js";


@Module({
    imports: [
        UserModule,
        PassportModule.register({
            session: false,
        }),
    ],
    providers: [OauthAccountService, GoogleStrategy, GithubStrategy],
    exports: [OauthAccountService]
})
export class OauthAccountModule{}