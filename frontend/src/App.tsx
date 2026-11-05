import { Admin } from './pages/Admin'
import { Auth } from './pages/Auth'
import { Browse } from './pages/Browse'
import { Cart } from './pages/Cart'
import { NewListing } from './pages/NewListing'
import { OwnerToday } from './pages/OwnerToday'
import { Pickup } from './pages/Pickup'
import { Reservations } from './pages/Reservations'
import { useRoute } from './lib/route'

/**
 * Draft frontend: every screen from the ThriftBite design canvas, running on
 * sample data (src/data/sample.ts). Screens are switched by the URL hash, e.g.
 * #/cart or #/owner. Wiring to the Spring Boot API comes next (src/api).
 */
export default function App() {
  const path = useRoute()
  switch (path) {
    case '/cart': return <Cart />
    case '/reservations': return <Reservations />
    case '/pickup': return <Pickup />
    case '/login': return <Auth />
    case '/owner': return <OwnerToday />
    case '/owner/new': return <NewListing />
    case '/admin': return <Admin />
    default: return <Browse />
  }
}
