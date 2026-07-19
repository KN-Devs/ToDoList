import { test, expect } from '@playwright/test';
import { register, uniqueUser } from './helpers';

test.describe('Cycle de vie projet + tâche', () => {
  test('créer un projet, y créer une tâche, changer son statut, la modifier puis la supprimer', async ({ page }) => {
    const user = uniqueUser('lifecycle');
    await register(page, user);

    // Créer un projet
    await page.getByRole('tab', { name: 'Créer un projet' }).click();
    await page.fill('input[name="nom"]', 'Projet E2E');
    await page.fill('textarea[name="description"]', 'Projet créé par un test end-to-end');
    await page.fill('input[name="startDate"]', '2026-01-01');
    await page.fill('input[name="endDate"]', '2026-12-31');
    await page.click('form.project-form button[type="submit"]');

    await expect(page.locator('.project-card')).toContainText('Projet E2E');

    // Ouvrir le projet
    await page.locator('.project-card').click();
    await expect(page.locator('h1')).toHaveText('Projet E2E');

    // Créer une tâche
    await page.getByRole('tab', { name: 'Créer une tâche' }).click();
    await page.fill('form.task-form input[name="nom"]', 'Tâche E2E');
    await page.fill('form.task-form textarea[name="description"]', 'Créée par un test end-to-end');
    await page.click('form.task-form button[type="submit"]');

    const taskCard = page.locator('.task-card', { hasText: 'Tâche E2E' });
    await expect(taskCard).toBeVisible();
    await expect(taskCard.locator('.status-select')).toHaveValue('TODO');

    // Changer le statut via le menu déroulant du Kanban
    await taskCard.locator('.status-select').selectOption('DONE');
    await expect(taskCard.locator('.status-select')).toHaveValue('DONE');

    // Ouvrir le détail (cliquer sur le titre pour éviter le menu de statut) puis modifier
    await taskCard.locator('h3').click();
    const modal = page.locator('.modal-card');
    await expect(modal.locator('h2')).toHaveText('Tâche E2E');
    await modal.getByRole('button', { name: 'Modifier' }).click();

    await page.getByLabel('Nom').fill('Tâche E2E modifiée');
    await page.getByRole('button', { name: 'Enregistrer' }).click();

    const updatedCard = page.locator('.task-card', { hasText: 'Tâche E2E modifiée' });
    await expect(updatedCard).toBeVisible();

    // Supprimer la tâche
    page.once('dialog', (dialog) => dialog.accept());
    await updatedCard.getByRole('button', { name: 'Supprimer' }).click();

    await expect(page.locator('.task-card')).toHaveCount(0);
  });
});
