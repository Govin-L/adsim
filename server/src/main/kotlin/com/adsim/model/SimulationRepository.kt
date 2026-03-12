package com.adsim.model

import org.springframework.data.mongodb.repository.MongoRepository

interface SimulationRepository : MongoRepository<Simulation, String> {
    fun findAllByOrderByCreatedAtDesc(): List<Simulation>
    fun findByStatusIn(statuses: List<SimulationStatus>): List<Simulation>
}

interface AgentRepository : MongoRepository<Agent, String> {
    fun findBySimulationId(simulationId: String): List<Agent>
    fun countBySimulationId(simulationId: String): Long
    fun countBySimulationIdAndDecisionsIsNotNull(simulationId: String): Long
}
