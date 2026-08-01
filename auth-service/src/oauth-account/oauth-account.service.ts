import {Injectable} from "@nestjs/common";
import {IAccount} from "../account/interfaces/account.interface.js";
import {IOAuthUser} from "./interfaces/oauth-user.interface.js";
import {PrismaService} from "../prisma/prisma.service.js";
import {AccountService} from "../account/account.service.js";


@Injectable()
export class OauthAccountService {

    constructor(
        private readonly prisma: PrismaService,
        private readonly accountService: AccountService,
    ) {}

    async loginWithOauth(profile: IOAuthUser) : Promise<IAccount> {
        const oauthAccount = await this.prisma.oauthAccount.findFirst({
            where: {
                provider: profile.provider,
                providerAccountId: profile.providerAccountId,
            },
            include: {
                account: true,
            }
        })

        if(oauthAccount) return oauthAccount.account;

        let account: IAccount | null = await this.prisma.account.findUnique({
            where: {
                email: profile.email,
            }
        })

        if (!account) {
            account = await this.accountService.createAccount({
                    email: profile.email,
                    fullName: profile.fullName,
                    displayName: profile.fullName,
                    passwordHash: null,
                    emailVerified: true,
            })
        }

        await this.prisma.oauthAccount.create({
            data: {
                provider: profile.provider,
                providerAccountId: profile.providerAccountId,
                providerEmail: profile.email,
                accountId: account.id,
            },
        });

        return account;
    }

}