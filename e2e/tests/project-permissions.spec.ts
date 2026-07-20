import { test, expect } from '@playwright/test';
import { findPendingProjectInvitationToken } from './db';
import { register, uniqueUser } from './helpers';

test.describe('Permissions membre de projet', () => {
  test('un membre sans droit peut voir et changer le statut, mais pas créer/modifier/supprimer, jusqu\'à ce que le droit soit accordé', async ({ browser }) => {
    const owner = uniqueUser('owner');
    const member = uniqueUser('member');

    const ownerContext = await browser.newContext();
    const memberContext = await browser.newContext();
    const ownerPage = await ownerContext.newPage();
    const memberPage = await memberContext.newPage();

    try {
      // Le membre doit exister avant que le propriétaire ne l'ajoute au projet
      await register(memberPage, member);

      // Le propriétaire crée un projet et une tâche
      await register(ownerPage, owner);
      await ownerPage.getByRole('tab', { name: 'Créer un projet' }).click();
      await ownerPage.fill('input[name="nom"]', 'Projet Permissions E2E');
      await ownerPage.fill('textarea[name="description"]', 'Vérification du modèle de permissions');
      await ownerPage.fill('input[name="startDate"]', '2026-01-01');
      await ownerPage.fill('input[name="endDate"]', '2026-12-31');
      await ownerPage.click('form.project-form button[type="submit"]');
      await ownerPage.locator('.project-card').click();
      await ownerPage.waitForURL(/\/projects\/\d+/);

      await ownerPage.getByRole('tab', { name: 'Créer une tâche' }).click();
      await ownerPage.fill('form.task-form input[name="nom"]', 'Tâche partagée');
      await ownerPage.fill('form.task-form textarea[name="description"]', 'Visible par le membre');
      await ownerPage.click('form.task-form button[type="submit"]');
      await expect(ownerPage.locator('.task-card')).toBeVisible();

      // Le propriétaire invite le membre : l'invitation reste en attente tant
      // qu'elle n'est pas acceptée, elle n'ajoute pas directement le membre
      await ownerPage.fill('input[name="newMemberEmail"]', member.email);
      await ownerPage.click('form.add-member-form button[type="submit"]');
      await expect(ownerPage.locator('.member-list li')).toContainText(member.email);
      await expect(ownerPage.locator('.member-list li').getByRole('button', { name: 'Annuler' })).toBeVisible();

      // Le membre accepte l'invitation reçue par email (récupérée en base,
      // l'envoi d'email étant désactivé dans cet environnement de test)
      const invitationToken = await findPendingProjectInvitationToken(member.email);
      await memberPage.goto(`/invitations/accept?token=${invitationToken}`);
      await memberPage.getByRole('button', { name: 'Voir le projet' }).click();

      // Le membre est maintenant sur le projet : accès en lecture seule
      await expect(memberPage.locator('h1')).toHaveText('Projet Permissions E2E');
      await expect(memberPage.getByRole('tab', { name: 'Créer une tâche' })).toHaveCount(0);
      await expect(memberPage.locator('.project-card-actions')).toHaveCount(0);
      await expect(memberPage.locator('form.add-member-form')).toHaveCount(0);

      const memberTaskCard = memberPage.locator('.task-card', { hasText: 'Tâche partagée' });
      await expect(memberTaskCard).toBeVisible();
      await expect(memberTaskCard.locator('.task-card-actions')).toHaveCount(0);

      // Mais peut changer le statut
      await memberTaskCard.locator('.status-select').selectOption('IN_PROGRESS');
      await expect(memberTaskCard.locator('.status-select')).toHaveValue('IN_PROGRESS');

      // Le propriétaire recharge sa page pour voir le membre désormais accepté
      // (l'acceptation a eu lieu dans le contexte de navigateur du membre)
      await ownerPage.reload();
      await expect(ownerPage.locator('.member-list li')).toContainText(member.email);

      // Le propriétaire accorde le droit de gérer les tâches (on attend la vraie réponse
      // du PATCH : cocher la case ne garantit pas que l'appel a réussi côté serveur)
      const permissionUpdated = ownerPage.waitForResponse(
        (res) => res.url().includes('/members/') && res.request().method() === 'PATCH' && res.ok()
      );
      await ownerPage.locator('.permission-toggle input[type="checkbox"]').check();
      await permissionUpdated;

      // Le membre, une fois la page rechargée, peut désormais créer une tâche
      await memberPage.reload();
      await expect(memberPage.getByRole('tab', { name: 'Créer une tâche' })).toBeVisible();
      await memberPage.getByRole('tab', { name: 'Créer une tâche' }).click();
      await memberPage.fill('form.task-form input[name="nom"]', 'Tâche du membre');
      await memberPage.fill('form.task-form textarea[name="description"]', 'Créée après obtention du droit');
      await memberPage.click('form.task-form button[type="submit"]');

      await expect(memberPage.locator('.task-card', { hasText: 'Tâche du membre' })).toBeVisible();
    } finally {
      await ownerContext.close();
      await memberContext.close();
    }
  });
});
