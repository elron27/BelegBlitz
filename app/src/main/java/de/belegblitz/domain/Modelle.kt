package de.belegblitz.domain

/** Krankenkasse, bei der eine Rechnung eingereicht wird (SR-7d1f8913431f). */
enum class Kasse { DAK, VKB }

/** Status einer Einreichung (SR-7d1f8913431f, Akzeptanzkriterium 2). */
enum class EinreichungStatus { ERFASST, EINGEREICHT, ERSTATTET, TEILWEISE_ERSTATTET, ABGELEHNT }