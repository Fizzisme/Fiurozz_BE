export interface IOAuthUser {
    provider: "google" | "github" | "facebook";
    providerAccountId: string;
    fullName: string;
    email: string;
    avatar?: string;
}