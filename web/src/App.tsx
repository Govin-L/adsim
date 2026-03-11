import { BrowserRouter, Routes, Route } from 'react-router-dom'
import CreateSimulation from './pages/CreateSimulation'
import SimulationResult from './pages/SimulationResult'
import AgentInterview from './pages/AgentInterview'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<CreateSimulation />} />
        <Route path="/simulation/:id" element={<SimulationResult />} />
        <Route path="/simulation/:id/agent/:agentId" element={<AgentInterview />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
