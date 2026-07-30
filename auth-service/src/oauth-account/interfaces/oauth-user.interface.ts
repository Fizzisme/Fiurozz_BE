export interface IOAuthUser {
    provider: "google" | "github";
    providerUserId: string;
    fullName: string;
    email: string;
    avatar?: string;
}