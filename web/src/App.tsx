import { Suspense, lazy } from 'react'
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { Toaster } from 'sonner'

const CreateSimulation = lazy(() => import('./pages/CreateSimulation'))
const SimulationResult = lazy(() => import('./pages/SimulationResult'))
const AgentInterview = lazy(() => import('./pages/AgentInterview'))

function App() {
  return (
    <BrowserRouter>
      <Toaster position="bottom-center" richColors />
      <Suspense fallback={null}>
        <Routes>
          <Route path="/" element={<CreateSimulation />} />
          <Route path="/simulation/:id" element={<SimulationResult />} />
          <Route path="/simulation/:id/agent/:agentId" element={<AgentInterview />} />
        </Routes>
      </Suspense>
    </BrowserRouter>
  )
}

export default App
