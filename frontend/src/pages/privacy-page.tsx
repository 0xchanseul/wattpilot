import type { ReactNode } from 'react'
import { Link } from 'react-router'

const PRIVACY_CONTACT_EMAIL = 'kimseongeun526@gmail.com'
const LAST_UPDATED = '16 October 2026'

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="space-y-3">
      <h2 className="font-display text-xl font-semibold tracking-tight">{title}</h2>
      {children}
    </section>
  )
}

function List({ children }: { children: ReactNode }) {
  return <ul className="list-disc space-y-1.5 pl-5">{children}</ul>
}

export function PrivacyPage() {
  return (
    <div className="min-h-svh">
      <header className="border-border/70 border-b">
        <div className="mx-auto flex h-16 max-w-3xl items-center px-4 sm:px-6">
          <Link to="/" className="flex items-center gap-2">
            <img src="/favicon.svg" alt="" className="size-7" />
            <span className="font-display text-lg font-semibold tracking-tight">WattPilot</span>
          </Link>
        </div>
      </header>

      <main className="mx-auto max-w-3xl space-y-8 px-4 py-10 text-sm leading-relaxed sm:px-6">
        <div className="space-y-2">
          <h1 className="font-display text-3xl font-semibold tracking-tight">Privacy policy</h1>
          <p className="text-muted-foreground">Last updated: {LAST_UPDATED}</p>
        </div>

        <Section title="Who is responsible">
          <p>
            WattPilot is a personal project operated by Chanseul Kim, who is responsible for the
            personal data described here. For any question about your data, write to{' '}
            <a className="underline underline-offset-4" href={`mailto:${PRIVACY_CONTACT_EMAIL}`}>
              {PRIVACY_CONTACT_EMAIL}
            </a>
            .
          </p>
          <p>
            WattPilot helps you charge an electric vehicle when Norwegian electricity prices are
            lowest. Charging is simulated: the service never controls a real vehicle.
          </p>
        </Section>

        <Section title="What we store">
          <List>
            <li>
              <strong>Account:</strong> your email address, your name, your default price area and
              your password. The password is stored only as a one-way hash, never in readable form.
            </li>
            <li>
              <strong>Your EVs:</strong> the name, manufacturer, model, battery capacity and
              charging power you enter.
            </li>
            <li>
              <strong>Charging data:</strong> the charging plans and schedules you create, the
              simulated charging sessions, and the cost and savings estimates calculated from them.
            </li>
            <li>
              <strong>Connected vehicle (optional):</strong> if you connect a vehicle through
              Smartcar, we store the identifiers Smartcar returns and the vehicle make, model and
              year. We read the battery level and charging state, only to show them to you. We
              never store your vehicle account credentials.
            </li>
            <li>
              <strong>Feedback:</strong> the text you send. It is stored anonymously, with nothing
              that identifies you or your account.
            </li>
            <li>
              <strong>Technical data:</strong> our web server logs the IP address, time and address
              of each request. They are kept only for a limited period and used for operating and
              securing the service.
            </li>
          </List>
        </Section>

        <Section title="Why we use it">
          <p>
            Only to provide the service you asked for: signing you in, calculating the cheapest
            charging window for your EV, showing your history and savings, and keeping the service
            secure and running. We do not sell your data, show advertising, or build profiles.
          </p>
        </Section>

        <Section title="Who else is involved">
          <List>
            <li>
              <strong>Microsoft Azure</strong> hosts the application and its database in the EU
              (Sweden Central).
            </li>
            <li>
              <strong>Smartcar</strong> receives requests only if you choose to connect a vehicle,
              and only to read its battery and charging state.
            </li>
            <li>
              <strong>Hva koster strømmen</strong> provides public electricity prices. We request
              prices only, and send no personal data.
            </li>
            <li>
              <strong>UptimeRobot</strong> checks that the service is online. It requests public
              pages only and receives no personal data.
            </li>
          </List>
        </Section>

        <Section title="Cookies">
          <p>
            WattPilot uses one cookie, <code>wp_refresh_token</code>. It keeps you signed in and is
            needed for the service to work. It cannot be read by scripts on the page. We use no
            analytics, advertising or tracking cookies.
          </p>
        </Section>

        <Section title="How long we keep it and how to delete it">
          <List>
            <li>
              Your data is kept while your account exists. You can delete your account at any time
              from your <Link to="/profile" className="underline underline-offset-4">profile</Link>.
              Deletion is immediate and removes your account, EVs, charging data, vehicle
              connections and sessions.
            </li>
            <li>
              Database backups are kept for 7 days, so deleted data can remain in a backup for up
              to 7 days before it disappears.
            </li>
            <li>
              Feedback is anonymous and cannot be linked back to you, so it is not removed when
              you delete your account.
            </li>
            <li>
              Demo accounts are temporary and deleted automatically after about a day.
            </li>
          </List>
        </Section>

        <Section title="Your rights">
          <p>
            Under the GDPR you may ask for access to your data, correction of inaccurate data,
            deletion, a copy of your data, and you may object to how it is used. You can correct
            your name and price area yourself on the profile page and delete your account there.
            For anything else, write to{' '}
            <a className="underline underline-offset-4" href={`mailto:${PRIVACY_CONTACT_EMAIL}`}>
              {PRIVACY_CONTACT_EMAIL}
            </a>
            . You also have the right to complain to the Norwegian Data Protection Authority
            (Datatilsynet).
          </p>
        </Section>

        <Section title="Changes">
          <p>
            If this policy changes in a meaningful way, the date above is updated. The latest
            version is always available on this page.
          </p>
        </Section>
      </main>
    </div>
  )
}
