/*
  Warnings:

  - A unique constraint covering the columns `[display_name]` on the table `users` will be added. If there are existing duplicate values, this will fail.
  - Added the required column `display_name` to the `users` table without a default value. This is not possible if the table is not empty.
  - Added the required column `full_name` to the `users` table without a default value. This is not possible if the table is not empty.

*/
-- AlterTable
ALTER TABLE "users" ADD COLUMN     "display_name" TEXT NOT NULL,
ADD COLUMN     "full_name" TEXT NOT NULL;

-- CreateIndex
CREATE UNIQUE INDEX "users_display_name_key" ON "users"("display_name");
