import {Injectable} from "@nestjs/common";
import {IUser} from "../user/interfaces/user.interface.js";
import {IOAuthUser} from "./interfaces/oauth-user.interface.js";
import {PrismaService} from "../prisma/prisma.service.js";
import {UserService} from "../user/user.service.js";


@Injectable()
export class OauthAccountService {

    constructor(
        private readonly prisma: PrismaService,
        private readonly userService: UserService,
    ) {}

    async loginWithOauth(profile: IOAuthUser) : Promise<IUser> {
        const oauthAccount = await this.prisma.oauthAccount.findFirst({
            where: {
                provider: profile.provider,
                providerUserId: profile.providerUserId,
            },
            include: {
                user: true,
            }
        })

        if(oauthAccount) return oauthAccount.user;

        let user: IUser | null = await this.prisma.user.findUnique({
            where: {
                email: profile.email,
            }
        })

        if (!user) {
            user = await this.userService.createUser({
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
                providerUserId: profile.providerUserId,
                providerEmail: profile.email,
                userId: user.id,
            },
        });

        return user;
    }

}