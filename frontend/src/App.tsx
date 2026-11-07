import type { ReactNode } from 'react'
import type { Role } from './api/types'
import { AppFrame } from './components/AppFrame'
import { Loading, NeedsAccount } from './components/States'
import { useAuth } from './lib/auth-context'
import { match, paths, useRoute } from './lib/route'
import { Admin } from './pages/Admin'
import { Auth } from './pages/Auth'
import { Browse } from './pages/Browse'
import { Cart } from './pages/Cart'
import { Me, NotFound, Pending, Privacy } from './pages/Info'
import { NewListing } from './pages/NewListing'
import { OwnerToday } from './pages/OwnerToday'
import { Pickup } from './pages/Pickup'
import { Reservations } from './pages/Reservations'
import { Sales } from './pages/Sales'

const needs: Record<Role, 'buyer' | 'store' | 'admin'> = { CONSUMER: 'buyer', OWNER: 'store', ADMIN: 'admin' }

/** Shows the page only to the kind of account it's for. */
function Only({ role, children }: { role: Role; children: ReactNode }) {
  const { user } = useAuth()
  if (!user) return <NeedsAccount what="login" />
  if (user.role !== role) return <NeedsAccount what={needs[role]} />
  return <>{children}</>
}

function page(path: string): ReactNode {
  const pickup = match(path, '/pickup/:id')
  if (pickup) return <Only role="CONSUMER"><Pickup reservationId={pickup.id} /></Only>

  switch (path) {
    case paths.browse: return <Browse />
    case paths.cart: return <Only role="CONSUMER"><Cart /></Only>
    case paths.reservations: return <Only role="CONSUMER"><Reservations /></Only>
    case paths.owner: return <Only role="OWNER"><OwnerToday /></Only>
    case paths.newListing: return <Only role="OWNER"><NewListing /></Only>
    case paths.sales: return <Only role="OWNER"><Sales /></Only>
    case paths.admin: return <Only role="ADMIN"><Admin /></Only>
    case paths.pending: return <Pending />
    case paths.privacy: return <Privacy />
    case '/me': return <Me />
    default: return <NotFound />
  }
}

export default function App() {
  const path = useRoute()
  const { ready } = useAuth()

  if (path === paths.login || path === paths.signup) return <Auth mode={path === paths.signup ? 'signup' : 'login'} />
  return <AppFrame>{ready ? page(path) : <Loading />}</AppFrame>
}
