export interface IUser{
    id: string,
    fullName: string,
    displayName: string,
    email: string,
    passwordHash: string | null,
    emailVerified?: boolean,
    status: string,
    roles: string[],
    createdAt: Date,
    updatedAt: Date,
    lastLoginAt: Date | null,
}