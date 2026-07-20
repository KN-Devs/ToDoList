import { Client } from 'pg';

// Les tests E2E tournent contre une vraie base Postgres (docker run local ou
// service CI), avec les mêmes identifiants que ceux documentés dans le README.
// L'envoi d'email est désactivé (MAIL_ENABLED=false) dans cet environnement :
// on récupère donc directement le token d'invitation en base, comme le ferait
// un utilisateur cliquant sur le lien reçu par email.
async function withClient<T>(fn: (client: Client) => Promise<T>): Promise<T> {
  const client = new Client({
    host: process.env.E2E_DB_HOST ?? 'localhost',
    port: Number(process.env.E2E_DB_PORT ?? 5432),
    database: process.env.E2E_DB_NAME ?? 'todolist',
    user: process.env.E2E_DB_USER ?? 'todolist',
    password: process.env.E2E_DB_PASSWORD ?? 'todolist',
  });
  await client.connect();
  try {
    return await fn(client);
  } finally {
    await client.end();
  }
}

async function findPendingToken(email: string, type: 'PROJECT_INVITATION' | 'EMAIL_VERIFICATION'): Promise<string> {
  return withClient(async (client) => {
    const result = await client.query<{ token: string }>(
      `SELECT vt.token
       FROM verification_tokens vt
       JOIN users u ON u.user_id = vt.user_id
       WHERE u.email = $1 AND vt.type = $2 AND vt.consumed_at IS NULL
       ORDER BY vt.created_at DESC
       LIMIT 1`,
      [email, type]
    );

    if (result.rows.length === 0) {
      throw new Error(`Aucun token ${type} en attente pour ${email}`);
    }

    return result.rows[0].token;
  });
}

export function findPendingProjectInvitationToken(email: string): Promise<string> {
  return findPendingToken(email, 'PROJECT_INVITATION');
}

export function findPendingEmailVerificationToken(email: string): Promise<string> {
  return findPendingToken(email, 'EMAIL_VERIFICATION');
}
