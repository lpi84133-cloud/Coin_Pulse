package com.coinpulse.coinpulsegame.game

import android.graphics.Bitmap
import com.coinpulse.coinpulsegame.audio.SoundManager
import com.coinpulse.coinpulsegame.data.GameStore
import com.coinpulse.coinpulsegame.data.Upgrade
import com.coinpulse.coinpulsegame.ui.C
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * All gameplay state and rules. Runs on the main thread driven by the view's
 * Choreographer loop, so there are no cross-thread races (the old SurfaceView
 * version mutated entity lists from a worker thread).
 */
class Engine(
    private val store: GameStore,
    private val assets: Assets,
    private val sound: SoundManager,
) {

    // ---------- callbacks ----------
    var onWaveComplete: ((options: List<RunUpgrade>, choose: (RunUpgrade) -> Unit) -> Unit)? = null
    var onEnded: ((GameResult) -> Unit)? = null
    var onVibrate: ((Long) -> Unit)? = null

    // ---------- geometry ----------
    var w = 0f; private set
    var h = 0f; private set
    var minDim = 1f; private set

    // ---------- flags ----------
    var paused = false
    var ended = false; private set
    var started = false; private set

    // ---------- config derived from permanent upgrades ----------
    private val diff = store.arena().difficulty
    val maxEnergy = 3 + store.upgradeLevel(Upgrade.MAX_ENERGY)
    private val lvlRadius = store.upgradeLevel(Upgrade.PULSE_RADIUS)
    private val lvlPower = store.upgradeLevel(Upgrade.PULSE_POWER)
    private val lvlRecharge = store.upgradeLevel(Upgrade.RECHARGE)
    private val lvlMagnet = store.upgradeLevel(Upgrade.MAGNET)
    private val lvlSpeed = store.upgradeLevel(Upgrade.MOVE_SPEED)

    // ---------- run modifiers (chosen between waves) ----------
    private var powerMult = 1f
    private var radiusMult = 1f
    private var chargeMult = 1f
    private var moveMult = 1f
    private var magnetMult = 1f
    private var echoWave = false

    // ---------- player ----------
    var px = 0f; private set
    var py = 0f; private set
    var pvx = 0f; private set
    var pvy = 0f; private set
    var pr = 0f; private set
    var energy = 0; private set
    var invuln = 0f; private set
    var hitFlash = 0f; private set
    var playerBmp: Bitmap = assets.playerCoinDefault; private set

    // ---------- pulse ----------
    var charging = false; private set
    var charge = 0f; private set
    var chargeLock = 0f; private set
    var crystalBuff = 0f; private set
    var echoCharges = 0; private set

    // ---------- input ----------
    var joyActive = false; private set
    var joyOx = 0f; private set
    var joyOy = 0f; private set
    var joyX = 0f; private set
    var joyY = 0f; private set
    var joyRadius = 0f; private set

    // ---------- wave ----------
    var wave = 0; private set
    var waveTimer = 0f; private set
    var waveDuration = 1f; private set
    var bossAlive = false; private set
    var bossHp = 0f; private set
    var bossMaxHp = 1f; private set
    val finalWave = 6
    private var spawnTimer = 0f
    private var spawnInterval = 1f
    private var waitingUpgrade = false

    // ---------- score ----------
    var score = 0; private set
    var kills = 0; private set
    var combo = 0; private set
    var bestCombo = 0; private set
    private var comboTimer = 0f
    private var startTime = 0L

    // ---------- juice ----------
    var shake = 0f; private set
    var slowmo = 0f; private set

    // ---------- entities ----------
    val enemies = ArrayList<Enemy>(64)
    val orbs = ArrayList<Orb>(32)
    val waves = ArrayList<PulseWave>(8)
    val particles = Array(300) { Particle() }
    val floaters = Array(48) { Floater() }
    val portals = ArrayList<FloatPair>(5)
    private var particleCursor = 0
    private var floaterCursor = 0

    private var orbTimer = 0f
    private val rnd = Random(System.nanoTime())

    // ---------- entity types ----------
    class FloatPair(val x: Float, val y: Float)

    class Enemy(
        var x: Float, var y: Float,
        val kind: EnemyKind,
        var hp: Float, val maxHp: Float,
        val speed: Float, val radius: Float,
        val bmp: Bitmap, val scoreValue: Int,
    ) {
        var vx = 0f; var vy = 0f
        var spawnIn = 0.45f      // scale-in; harmless while > 0
        var stun = 0f
        var flash = 0f
        var barTimer = 0f
        var wobble = 0f
    }

    enum class OrbKind { COIN, CRYSTAL, STAR, BELL, FRUIT, NUMBER }

    class Orb(var x: Float, var y: Float, val kind: OrbKind, val bmp: Bitmap, val radius: Float) {
        var scale = 0f
        var bob = 0f
    }

    class PulseWave(val x: Float, val y: Float, val target: Float, val power: Float, val knock: Float) {
        var r = 0f
        val hitEnemies = HashSet<Enemy>()
        val hitOrbs = HashSet<Orb>()
    }

    class Particle {
        var active = false
        var x = 0f; var y = 0f; var vx = 0f; var vy = 0f
        var life = 0f; var maxLife = 1f; var size = 0f; var color = 0
    }

    class Floater {
        var active = false
        var text = ""
        var x = 0f; var y = 0f
        var life = 0f; var maxLife = 1f
        var color = 0; var size = 0f
    }

    // ---------- lifecycle ----------
    fun resize(newW: Int, newH: Int) {
        w = newW.toFloat(); h = newH.toFloat()
        minDim = min(w, h)
        pr = minDim * 0.046f
        joyRadius = minDim * 0.11f
        buildPortals()
        if (!started) start()
        px = px.coerceIn(pr, w - pr)
        py = py.coerceIn(pr, h - pr)
    }

    private fun start() {
        started = true
        energy = maxEnergy
        px = w / 2f; py = h / 2f
        playerBmp = try { assets.loadBitmap(store.skinAsset()) } catch (e: Exception) { assets.playerCoinDefault }
        startTime = System.currentTimeMillis()
        startWave(1)
    }

    private fun buildPortals() {
        portals.clear()
        portals.add(FloatPair(w * 0.10f, h * 0.20f))
        portals.add(FloatPair(w * 0.90f, h * 0.20f))
        portals.add(FloatPair(w * 0.10f, h * 0.82f))
        portals.add(FloatPair(w * 0.90f, h * 0.82f))
        portals.add(FloatPair(w * 0.50f, h * 0.92f))
    }

    // ---------- input ----------
    fun joystickDown(x: Float, y: Float) {
        if (paused || ended) return
        joyActive = true; joyOx = x; joyOy = y; joyX = x; joyY = y
    }

    fun joystickMove(x: Float, y: Float) {
        if (!joyActive) return
        joyX = x; joyY = y
    }

    fun joystickUp() {
        joyActive = false
    }

    fun pulseDown() {
        if (paused || ended || chargeLock > 0f) return
        charging = true
        charge = 0f
    }

    fun pulseUp() {
        if (!charging) return
        charging = false
        firePulse()
    }

    // ---------- derived stats ----------
    private fun chargeTime(): Float =
        ((1.25f - lvlRecharge * 0.075f) / chargeMult).coerceAtLeast(0.45f)

    private fun pulseTarget(c: Float): Float =
        ((0.115f + lvlRadius * 0.012f) + 0.30f * c.pow(0.9f)) * minDim * radiusMult

    private fun pulseDamage(c: Float): Float =
        (0.5f + 2.0f * c) * (1f + lvlPower * 0.35f) * powerMult * (1f + crystalBuff)

    private fun magnetRange(): Float = (0.10f + lvlMagnet * 0.024f) * minDim * magnetMult

    private fun moveSpeed(): Float = (0.60f + lvlSpeed * 0.055f) * minDim * moveMult

    fun previewRadius(): Float = pulseTarget(charge)

    // ---------- main update ----------
    fun update(rawDt: Float) {
        if (ended || paused) return
        val dt = if (slowmo > 0f) rawDt * 0.35f else rawDt
        slowmo = (slowmo - rawDt).coerceAtLeast(0f)

        invuln = (invuln - dt).coerceAtLeast(0f)
        hitFlash = (hitFlash - dt * 2.4f).coerceAtLeast(0f)
        chargeLock = (chargeLock - dt).coerceAtLeast(0f)
        shake *= (1f - min(1f, dt * 7f))
        if (comboTimer > 0f) {
            comboTimer -= dt
            if (comboTimer <= 0f) combo = 0
        }

        if (charging) {
            charge = min(1f, charge + dt / chargeTime())
        }

        updatePlayer(dt)
        updateWave(dt)
        updateEnemies(dt)
        updateOrbs(dt)
        updatePulses(dt)
        updateParticles(dt)
        updateFloaters(dt)
    }

    private fun updatePlayer(dt: Float) {
        var dx = 0f; var dy = 0f
        if (joyActive) {
            val rx = joyX - joyOx; val ry = joyY - joyOy
            val d = hypot(rx, ry)
            if (d > minDim * 0.012f) {
                val mag = min(1f, d / joyRadius)
                dx = rx / d * mag; dy = ry / d * mag
            }
        }
        val speed = moveSpeed()
        val targetVx = dx * speed
        val targetVy = dy * speed
        // smooth acceleration gives the coin weight without feeling sluggish
        val accel = min(1f, dt * 12f)
        pvx += (targetVx - pvx) * accel
        pvy += (targetVy - pvy) * accel
        px = (px + pvx * dt).coerceIn(pr, w - pr)
        py = (py + pvy * dt).coerceIn(pr, h - pr)
    }

    private fun updateWave(dt: Float) {
        if (waitingUpgrade) return
        waveTimer = (waveTimer - dt).coerceAtLeast(0f)

        val cap = 9 + wave * 3
        if (waveTimer > 0f && enemies.size < cap) {
            spawnTimer -= dt
            if (spawnTimer <= 0f) {
                spawnTimer = spawnInterval
                spawnEnemy()
            }
        }

        orbTimer -= dt
        if (orbTimer <= 0f && orbs.size < 16) {
            orbTimer = 0.85f + rnd.nextFloat() * 0.6f
            spawnOrb(randomOrbKind(), randomFieldX(), randomFieldY())
        }

        if (waveTimer <= 0f && !bossAlive) completeWave()
    }

    private fun startWave(index: Int) {
        wave = index
        waveDuration = 20f + index * 4f
        waveTimer = waveDuration
        spawnInterval = ((1.45f - index * 0.13f) / diff).coerceAtLeast(0.38f)
        spawnTimer = 0.7f
        if (index == 3 || index == finalWave) spawnGuardian(index == finalWave)
    }

    private fun completeWave() {
        // clear the field so there is never dead time hunting stragglers
        for (e in enemies) {
            score += e.scoreValue / 2
            burst(e.x, e.y, C.cyan, 8, minDim * 0.010f)
        }
        enemies.clear()

        if (wave >= finalWave) {
            finish(true)
            return
        }

        waitingUpgrade = true
        paused = true
        sound.play("reward")
        val options = RunUpgrade.values().toMutableList().apply { shuffle() }.take(3)
        onWaveComplete?.invoke(options) { chosen ->
            applyRunUpgrade(chosen)
            sound.play("upgrade")
            waitingUpgrade = false
            paused = false
            startWave(wave + 1)
        }
    }

    private fun applyRunUpgrade(u: RunUpgrade) {
        when (u) {
            RunUpgrade.POWER -> powerMult += 0.35f
            RunUpgrade.CHARGE -> chargeMult += 0.25f
            RunUpgrade.RADIUS -> radiusMult += 0.25f
            RunUpgrade.MOVE -> moveMult += 0.20f
            RunUpgrade.MAGNET -> magnetMult += 0.40f
            RunUpgrade.HEAL -> energy = min(maxEnergy, energy + 2)
            RunUpgrade.DOUBLE -> echoWave = true
        }
    }

    // ---------- spawning ----------
    private fun spawnEnemy() {
        val portal = pickPortal()
        val roll = rnd.nextFloat()
        val kind = when {
            wave >= 2 && roll < 0.22f -> EnemyKind.BRUTE
            wave >= 2 && roll < 0.48f -> EnemyKind.SPRINTER
            else -> EnemyKind.DRIFTER
        }
        val scale = (1f + (wave - 1) * 0.14f) * diff
        val e = when (kind) {
            EnemyKind.BRUTE -> Enemy(
                portal.x, portal.y, kind, 6f * scale, 6f * scale,
                0.072f * minDim, 0.042f * minDim, pickBmp(assets.enemiesMedium), 22
            )
            EnemyKind.SPRINTER -> Enemy(
                portal.x, portal.y, kind, 1.5f * scale, 1.5f * scale,
                0.165f * minDim, 0.026f * minDim, pickBmp(assets.enemiesSmall, tail = true), 16
            )
            else -> Enemy(
                portal.x, portal.y, kind, 2f * scale, 2f * scale,
                0.105f * minDim, 0.030f * minDim, pickBmp(assets.enemiesSmall), 10
            )
        }
        enemies.add(e)
        burst(portal.x, portal.y, C.purple, 8, minDim * 0.008f)
    }

    private fun spawnGuardian(isFinal: Boolean) {
        val portal = portals[0]
        val hp = (34f * diff) * (if (isFinal) 1.7f else 1f)
        val e = Enemy(
            portal.x, portal.y, EnemyKind.GUARDIAN, hp, hp,
            0.055f * minDim, 0.085f * minDim, assets.guardianElite, 260
        )
        e.spawnIn = 0.9f
        enemies.add(e)
        bossAlive = true
        bossHp = hp; bossMaxHp = hp
        shake = minDim * 0.012f
        sound.play("crystal")
        floater("GUARDIAN INCOMING", w / 2f, h * 0.30f, C.red, minDim * 0.045f, 2.2f)
    }

    private fun pickPortal(): FloatPair {
        // avoid spawning right on top of the player
        val far = portals.filter { hypot(it.x - px, it.y - py) > minDim * 0.28f }
        val pool = if (far.isEmpty()) portals else far
        return pool[rnd.nextInt(pool.size)]
    }

    private fun pickBmp(list: List<Bitmap>, tail: Boolean = false): Bitmap {
        if (list.isEmpty()) return assets.playerCoinDefault
        return if (tail && list.size > 2) list[2 + rnd.nextInt(list.size - 2)]
        else list[rnd.nextInt(min(2, list.size))]
    }

    private fun randomFieldX() = w * 0.08f + rnd.nextFloat() * w * 0.84f
    private fun randomFieldY() = h * 0.12f + rnd.nextFloat() * h * 0.78f

    private fun randomOrbKind(): OrbKind {
        val r = rnd.nextFloat()
        return when {
            r < 0.50f -> OrbKind.COIN
            r < 0.63f -> OrbKind.CRYSTAL
            r < 0.75f -> OrbKind.STAR
            r < 0.84f -> OrbKind.BELL
            r < 0.93f -> OrbKind.FRUIT
            else -> OrbKind.NUMBER
        }
    }

    private fun spawnOrb(kind: OrbKind, x: Float, y: Float) {
        val bmp = when (kind) {
            OrbKind.COIN -> assets.coins
            OrbKind.CRYSTAL -> assets.crystals
            OrbKind.STAR -> assets.stars
            OrbKind.BELL -> assets.bells
            OrbKind.FRUIT -> assets.fruits
            OrbKind.NUMBER -> assets.numbers
        }.let { if (it.isEmpty()) null else it[rnd.nextInt(it.size)] } ?: return
        orbs.add(Orb(x, y, kind, bmp, minDim * 0.026f))
    }

    // ---------- enemies ----------
    private fun updateEnemies(dt: Float) {
        var i = 0
        while (i < enemies.size) {
            if (ended) return
            val e = enemies[i]
            e.wobble += dt
            if (e.spawnIn > 0f) {
                e.spawnIn -= dt
                i++
                continue
            }
            e.flash = (e.flash - dt * 4f).coerceAtLeast(0f)
            e.barTimer = (e.barTimer - dt).coerceAtLeast(0f)

            if (e.stun > 0f) {
                e.stun -= dt
                e.x += e.vx * dt; e.y += e.vy * dt
                val friction = 1f - min(1f, dt * 5f)
                e.vx *= friction; e.vy *= friction
            } else {
                val dx = px - e.x; val dy = py - e.y
                val d = hypot(dx, dy).coerceAtLeast(0.001f)
                e.x += dx / d * e.speed * dt
                e.y += dy / d * e.speed * dt
            }
            e.x = e.x.coerceIn(0f, w); e.y = e.y.coerceIn(0f, h)

            // contact
            val d = hypot(px - e.x, py - e.y)
            if (e.stun <= 0f && d < pr + e.radius) {
                if (invuln <= 0f) damagePlayer()
                // always bounce the enemy off so it cannot sit inside the coin
                val nx = (e.x - px) / d.coerceAtLeast(0.001f)
                val ny = (e.y - py) / d.coerceAtLeast(0.001f)
                e.vx = nx * minDim * 0.9f; e.vy = ny * minDim * 0.9f
                e.stun = 0.85f
            }
            i++
        }
    }

    private fun damagePlayer() {
        energy--
        invuln = 1.15f
        hitFlash = 1f
        shake = minDim * 0.02f
        sound.play("defeat", 0.5f, 1.6f)
        onVibrate?.invoke(45L)
        burst(px, py, C.red, 16, minDim * 0.012f)
        combo = 0
        if (energy <= 0) finish(false)
    }

    // ---------- orbs ----------
    private fun updateOrbs(dt: Float) {
        val magnet = magnetRange()
        var i = 0
        while (i < orbs.size) {
            val o = orbs[i]
            o.bob += dt
            if (o.scale < 1f) o.scale = min(1f, o.scale + dt * 3.5f)
            val dx = px - o.x; val dy = py - o.y
            val d = hypot(dx, dy).coerceAtLeast(0.001f)
            if (d < magnet) {
                val pull = (1f - d / magnet).pow(1.4f) * minDim * 3.2f * dt
                o.x += dx / d * pull; o.y += dy / d * pull
            }
            if (d < pr + o.radius) {
                collect(o)
                orbs.removeAt(i)
                continue
            }
            i++
        }
    }

    private fun collect(o: Orb) {
        when (o.kind) {
            OrbKind.COIN -> {
                val v = (10 * scoreMultiplier()).toInt()
                score += v
                sound.play("coin", 0.8f)
                floater("+$v", o.x, o.y, C.gold, minDim * 0.030f, 0.7f)
            }
            OrbKind.CRYSTAL -> {
                crystalBuff += 0.6f
                score += 5
                sound.play("crystal")
                floater("POWER UP", o.x, o.y, C.purple, minDim * 0.030f, 0.9f)
            }
            OrbKind.STAR -> {
                if (charging) charge = 1f else chargeLock = 0f
                chargeMult += 0.06f
                score += 5
                sound.play("crystal", 1f, 1.2f)
                floater("CHARGE BOOST", o.x, o.y, C.cyan, minDim * 0.030f, 0.9f)
            }
            OrbKind.BELL -> {
                echoCharges += 2
                score += 5
                sound.play("crystal", 1f, 0.8f)
                floater("ECHO WAVE", o.x, o.y, C.goldLight, minDim * 0.030f, 0.9f)
            }
            OrbKind.FRUIT -> {
                if (energy < maxEnergy) {
                    energy++
                    floater("+1 ENERGY", o.x, o.y, C.green, minDim * 0.030f, 0.9f)
                } else {
                    score += 25
                    floater("+25", o.x, o.y, C.gold, minDim * 0.030f, 0.7f)
                }
                sound.play("reward")
            }
            OrbKind.NUMBER -> {
                combo += 2
                comboTimer = 4f
                score += 15
                sound.play("reward", 1f, 1.15f)
                floater("COMBO +2", o.x, o.y, C.orange, minDim * 0.030f, 0.9f)
            }
        }
        burst(o.x, o.y, C.goldLight, 7, minDim * 0.008f)
    }

    private fun scoreMultiplier(): Float = (1f + combo * 0.08f).coerceAtMost(3f)

    // ---------- pulses ----------
    private fun firePulse() {
        val c = charge.coerceAtLeast(0.12f)
        val target = pulseTarget(c)
        val dmg = pulseDamage(c)
        val knock = minDim * (0.35f + 0.55f * c)
        waves.add(PulseWave(px, py, target, dmg, knock))

        val echo = echoWave || echoCharges > 0
        if (echoCharges > 0) echoCharges--
        if (echo) waves.add(PulseWave(px, py, target * 0.72f, dmg * 0.6f, knock * 0.6f))

        crystalBuff = 0f
        charge = 0f
        chargeLock = 0.22f
        shake = minDim * (0.004f + 0.012f * c)
        sound.play("pulse", 0.6f + 0.4f * c, 0.9f + 0.25f * c)
    }

    private fun updatePulses(dt: Float) {
        var i = 0
        while (i < waves.size) {
            val p = waves[i]
            val prev = p.r
            val speed = p.target / 0.20f       // full expansion in ~0.2s: readable
            p.r = min(p.target, p.r + speed * dt)
            var chain = 0

            var j = 0
            while (j < enemies.size) {
                val e = enemies[j]
                if (e.spawnIn > 0f || p.hitEnemies.contains(e)) { j++; continue }
                val d = hypot(e.x - p.x, e.y - p.y)
                if (d <= p.r + e.radius && d >= prev - e.radius) {
                    p.hitEnemies.add(e)
                    e.hp -= p.power
                    e.flash = 1f
                    e.barTimer = 2.5f
                    val nx = (e.x - p.x) / d.coerceAtLeast(0.001f)
                    val ny = (e.y - p.y) / d.coerceAtLeast(0.001f)
                    e.vx = nx * p.knock; e.vy = ny * p.knock
                    e.stun = 0.35f
                    chain++
                    if (e.hp <= 0f) {
                        killEnemy(e)
                        enemies.removeAt(j)
                        continue
                    } else {
                        burst(e.x, e.y, C.cyan, 5, minDim * 0.007f)
                    }
                }
                j++
            }

            var k = 0
            while (k < orbs.size) {
                val o = orbs[k]
                if (p.hitOrbs.contains(o)) { k++; continue }
                val d = hypot(o.x - p.x, o.y - p.y)
                if (d <= p.r) {
                    p.hitOrbs.add(o)
                    collect(o)
                    orbs.removeAt(k)
                    chain++
                    continue
                }
                k++
            }

            if (chain > 0) {
                combo++
                comboTimer = 3.5f
                if (combo > bestCombo) bestCombo = combo
            }
            if (chain >= 3) {
                score += chain * 6
                sound.play("chain")
                shake = maxOf(shake, minDim * 0.014f)
                floater("CHAIN x$chain", p.x, p.y - p.target * 0.4f, C.orange, minDim * 0.040f, 1.0f)
            }

            if (p.r >= p.target) { waves.removeAt(i); continue }
            i++
        }
    }

    private fun killEnemy(e: Enemy) {
        kills++
        val v = (e.scoreValue * scoreMultiplier()).toInt()
        score += v
        burst(e.x, e.y, if (e.kind == EnemyKind.GUARDIAN) C.gold else C.cyan,
            if (e.kind == EnemyKind.GUARDIAN) 40 else 12, minDim * 0.011f)
        floater("+$v", e.x, e.y, C.text, minDim * 0.028f, 0.6f)
        if (e.kind == EnemyKind.GUARDIAN) {
            bossAlive = false
            shake = minDim * 0.03f
            slowmo = 0.45f
            sound.play("victory", 0.8f)
            floater("GUARDIAN DOWN", w / 2f, h * 0.32f, C.gold, minDim * 0.05f, 1.8f)
            repeat(3) { spawnOrb(OrbKind.COIN, e.x + rnd.nextFloat() * 60 - 30, e.y + rnd.nextFloat() * 60 - 30) }
        } else if (rnd.nextFloat() < 0.45f) {
            spawnOrb(OrbKind.COIN, e.x, e.y)
        }
        if (bossAlive) bossHp = enemies.firstOrNull { it.kind == EnemyKind.GUARDIAN }?.hp ?: 0f
    }

    // ---------- juice ----------
    private fun burst(x: Float, y: Float, color: Int, count: Int, size: Float) {
        repeat(count) {
            val p = nextParticle()
            val ang = rnd.nextFloat() * 6.2832f
            val sp = minDim * (0.08f + rnd.nextFloat() * 0.34f)
            p.active = true
            p.x = x; p.y = y
            p.vx = kotlin.math.cos(ang) * sp
            p.vy = kotlin.math.sin(ang) * sp
            p.maxLife = 0.35f + rnd.nextFloat() * 0.35f
            p.life = p.maxLife
            p.size = size * (0.6f + rnd.nextFloat() * 0.8f)
            p.color = color
        }
    }

    private fun nextParticle(): Particle {
        repeat(particles.size) {
            particleCursor = (particleCursor + 1) % particles.size
            val p = particles[particleCursor]
            if (!p.active) return p
        }
        return particles[particleCursor]
    }

    private fun floater(text: String, x: Float, y: Float, color: Int, size: Float, life: Float) {
        floaterCursor = (floaterCursor + 1) % floaters.size
        val f = floaters[floaterCursor]
        f.active = true; f.text = text
        f.x = x; f.y = y
        f.maxLife = life; f.life = life
        f.color = color; f.size = size
    }

    private fun updateParticles(dt: Float) {
        for (p in particles) {
            if (!p.active) continue
            p.life -= dt
            if (p.life <= 0f) { p.active = false; continue }
            p.x += p.vx * dt; p.y += p.vy * dt
            p.vx *= (1f - min(1f, dt * 2.4f))
            p.vy *= (1f - min(1f, dt * 2.4f))
        }
    }

    private fun updateFloaters(dt: Float) {
        for (f in floaters) {
            if (!f.active) continue
            f.life -= dt
            if (f.life <= 0f) { f.active = false; continue }
            f.y -= minDim * 0.05f * dt
        }
    }

    // ---------- end ----------
    private fun finish(victory: Boolean) {
        if (ended) return
        ended = true
        val survival = System.currentTimeMillis() - startTime
        val coinsEarned = score / 8 + wave * 20 + kills * 2 + bestCombo * 5
        sound.play(if (victory) "victory" else "defeat")
        store.recordRun(score, kills, wave, survival, coinsEarned, bestCombo)
        onEnded?.invoke(
            GameResult(victory, score, kills, wave, bestCombo, survival, coinsEarned)
        )
    }

    /** Distance the guardian hint should be shown (timer done but boss alive). */
    fun awaitingBossKill(): Boolean = waveTimer <= 0f && bossAlive
}
