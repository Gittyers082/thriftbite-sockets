import type { ReactNode } from 'react'
import type { Role } from './api/types'
import { AppFrame } from './components/AppFrame'
import { Loading, NeedsAccount } from './components/States'
import { useAuth } from './lib/auth-context'
import { match, paths, useRoute } from './lib/route'
import { Admin } from './pages/Admin'
import { AdminActivity } from './pages/AdminActivity'
import { AdminReports } from './pages/AdminReports'
import { AdminUsers } from './pages/AdminUsers'
import { Auth } from './pages/Auth'
import { Browse } from './pages/Browse'
import { Cart } from './pages/Cart'
import { Account } from './pages/Account'
import { Following } from './pages/Following'
import { NotFound, Pending, Privacy } from './pages/Info'
import { NewListing } from './pages/NewListing'
import { OwnerToday } from './pages/OwnerToday'
import { Pickup } from './pages/Pickup'
import { Recover } from './pages/Recover'
import { ReportProblem } from './pages/ReportProblem'
import { PostAgain } from './pages/PostAgain'
import { Reservations } from './pages/Reservations'
import { Sales } from './pages/Sales'
import { StorePage } from './pages/StorePage'
import { StoreProfilePage } from './pages/StoreProfile'

const needs: Record<Role, 'buyer' | 'store' | 'admin'> = { CONSUMER: 'buyer', OWNER: 'store', ADMIN: 'admin' }

/** Shows the page only to the kind of account it's for. */
function Only({ role, children }: { role: Role; children: ReactNode }) {
  const { user } = useAuth()
  if (!user) return <NeedsAccount what="login" />
  if (user.role !== role) return <NeedsAccount what={needs[role]} />
  return <>{children}</>
}

/** Shows the page to anyone who's logged in. */
function SignedIn({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  return user ? <>{children}</> : <NeedsAccount what="login" />
}

function page(path: string): ReactNode {
  const pickup = match(path, '/pickup/:id')
  if (pickup) return <Only role="CONSUMER"><Pickup reservationId={pickup.id} /></Only>
  const store = match(path, '/stores/:slug')
  if (store) return <StorePage slug={store.slug} />
  const reportPickup = match(path, '/report/pickup/:id')
  if (reportPickup) return <Only role="CONSUMER"><ReportProblem key={path} reservationId={reportPickup.id} /></Only>
  const reportStore = match(path, '/report/store/:slug')
  if (reportStore) return <Only role="CONSUMER"><ReportProblem key={path} storeSlug={reportStore.slug} /></Only>

  switch (path) {
    case paths.browse: return <Browse />
    case paths.cart: return <Only role="CONSUMER"><Cart /></Only>
    case paths.reservations: return <Only role="CONSUMER"><Reservations /></Only>
    case paths.following: return <Only role="CONSUMER"><Following /></Only>
    case paths.owner: return <Only role="OWNER"><OwnerToday /></Only>
    case paths.newListing: return <Only role="OWNER"><NewListing /></Only>
    case paths.postAgain: return <Only role="OWNER"><PostAgain /></Only>
    case paths.storeProfile: return <Only role="OWNER"><StoreProfilePage /></Only>
    case paths.sales: return <Only role="OWNER"><Sales /></Only>
    case paths.admin: return <Only role="ADMIN"><Admin /></Only>
    case paths.adminReports: return <Only role="ADMIN"><AdminReports /></Only>
    case paths.adminUsers: return <Only role="ADMIN"><AdminUsers /></Only>
    case paths.adminActivity: return <Only role="ADMIN"><AdminActivity /></Only>
    case paths.pending: return <Pending />
    case paths.privacy: return <Privacy />
    case paths.account: return <SignedIn><Account /></SignedIn>
    case paths.recover: return <Recover />
    default: return <NotFound />
  }
}

export default function App() {
  const path = useRoute()
  const { ready } = useAuth()

  if (path === paths.login || path === paths.signup) return <Auth mode={path === paths.signup ? 'signup' : 'login'} />
  return <AppFrame>{ready ? page(path) : <Loading />}</AppFrame>
}
