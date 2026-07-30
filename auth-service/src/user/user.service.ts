import {Injectable} from "@nestjs/common";
import {PrismaService} from "../prisma/prisma.service.js";
import {IUser} from "./interfaces/user.interface.js";

@Injectable()
export class UserService {
    constructor(private readonly prisma: PrismaService) {}

    async findUserByEmail(email: string):  Promise<IUser | null> {
        return this.prisma.user.findUnique({
            where: { email }
        })
    }

    async findUserById(id: string):  Promise<IUser | null> {
        return this.prisma.user.findUnique({
            where: { id }
        })
    }

    async createUser(data: Omit<IUser, 'id'  | 'status' | 'roles' | 'createdAt' | 'updatedAt' | 'lastLoginAt'>): Promise<IUser> {
        return this.prisma.user.create({
            data: {
                email: data.email,
                fullName:  data.fullName,
                displayName: data.displayName,
                passwordHash: data.passwordHash,
                emailVerified: data.emailVerified,
            }
        })
    }
}