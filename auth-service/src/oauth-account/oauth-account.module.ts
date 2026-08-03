import {Module} from "@nestjs/common";
import {OauthAccountService} from "./oauth-account.service.js";
import {AccountModule} from "../account/account.module.js";
import {GoogleStrategy} from "./strategy/google.strategy.js";
import {PassportModule} from "@nestjs/passport";
import {GithubStrategy} from "./strategy/github.strategy.js";
import {OutboxEventModule} from "../outboxEvent/outbox-event.module.js";


@Module({
    imports: [
        OutboxEventModule,
        AccountModule,
        PassportModule.register({
            session: false,
        }),
    ],
    providers: [OauthAccountService, GoogleStrategy, GithubStrategy],
    exports: [OauthAccountService]
})
export class OauthAccountModule{}