import { test, expect } from '@playwright/test';
import { confirmEmail, login, logout, register, uniqueUser } from './helpers';

test.describe('Authentification', () => {
  test('un nouvel utilisateur peut créer un compte et arrive sur la liste des projets', async ({ page }) => {
    const user = uniqueUser('register');

    await register(page, user);

    await expect(page.locator('h1')).toHaveText('Mes projets');
  });

  test('un utilisateur peut se déconnecter puis se reconnecter', async ({ page }) => {
    const user = uniqueUser('relogin');
    await register(page, user);

    // La connexion est bloquée tant que l'email n'est pas confirmé
    await confirmEmail(page, user.email);

    await logout(page);
    await login(page, user);

    await expect(page.locator('h1')).toHaveText('Mes projets');
  });

  test('un mauvais mot de passe affiche une erreur et ne connecte pas', async ({ page }) => {
    const user = uniqueUser('badpass');
    await register(page, user);
    await logout(page);

    await page.goto('/login');
    await page.fill('input[name="email"]', user.email);
    await page.fill('input[name="password"]', 'MauvaisMotDePasse1!');
    await page.click('button[type="submit"]');

    await expect(page.locator('.error')).toBeVisible();
    await expect(page).toHaveURL(/\/login/);
  });
});
