import { Page, expect } from '@playwright/test';
import { findPendingEmailVerificationToken } from './db';

export interface TestUser {
  nom: string;
  prenom: string;
  email: string;
  password: string;
}

export function uniqueUser(prefix: string): TestUser {
  const unique = `${Date.now()}-${Math.floor(Math.random() * 100000)}`;
  return {
    nom: 'Test',
    prenom: prefix,
    email: `${prefix}-${unique}@e2e.test`,
    password: 'Password123!',
  };
}

export async function register(page: Page, user: TestUser): Promise<void> {
  await page.goto('/register');
  await page.fill('input[name="nom"]', user.nom);
  await page.fill('input[name="prenom"]', user.prenom);
  await page.fill('input[name="email"]', user.email);
  await page.fill('input[name="password"]', user.password);
  await page.click('button[type="submit"]');

  // L'inscription n'enchaîne plus automatiquement : elle affiche un message
  // invitant à confirmer l'email avant de continuer vers l'application.
  await page.getByRole('button', { name: 'Continuer' }).click();
  await expect(page).toHaveURL(/\/projects/);
}

export async function login(page: Page, user: Pick<TestUser, 'email' | 'password'>): Promise<void> {
  await page.goto('/login');
  await page.fill('input[name="email"]', user.email);
  await page.fill('input[name="password"]', user.password);
  await page.click('button[type="submit"]');
  await expect(page).toHaveURL(/\/projects/);
}

export async function logout(page: Page): Promise<void> {
  await page.evaluate(() => localStorage.clear());
}

export async function confirmEmail(page: Page, email: string): Promise<void> {
  const token = await findPendingEmailVerificationToken(email);
  await page.goto(`/confirm-email?token=${token}`);
  await expect(page.locator('.modal-description')).toHaveText('Ton adresse email a bien été confirmée.');
}
