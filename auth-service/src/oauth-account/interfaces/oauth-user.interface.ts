export interface IOAuthUser {
    provider: "google" | "github";
    providerAccountId: string;
    fullName: string;
    email: string;
    avatar?: string;
}