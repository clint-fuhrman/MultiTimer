package io.clfm.multitimer.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimerDatabaseTest {

    private lateinit var timerDao: TimerDao
    private lateinit var database: TimerDatabase

    @Before
    fun createDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, TimerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        timerDao = database.timerDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun insertAndGetTimer_succeeds() = runBlocking {
        val timer = makeTimer()
        timerDao.insert(timer)
        assertEquals(timer.copy(id = 1, listPosition = 0), timerDao.getTimer(id = 1))
    }

    @Test
    fun insertAndGetMultipleTimers_succeeds() = runBlocking {
        for (id in 1..3) {
            val timer = makeTimer()
            timerDao.insert(timer)
            assertEquals(
                "Timer ID and/or list position not incremented as expected.",
                timer.copy(id = id, listPosition = id - 1),
                timerDao.getTimer(id = id)
            )
        }
        assertEquals(3, timerDao.getTimers().size)
    }

    @Test
    fun updateAndGetTimer_succeeds() = runBlocking {
        val timer = makeTimer()
        timerDao.insert(timer)

        val originalTimer = timerDao.getTimer(id = 1)
        val updatedTimer = originalTimer.copy(name = "New Name!")
        timerDao.update(updatedTimer)

        assertEquals(updatedTimer, timerDao.getTimer(id = 1))
    }

    @Test(expected = TimerDaoException::class)
    fun updateTimerListPosition_throwsException() = runBlocking {
        repeat(2) { timerDao.insert(makeTimer()) }

        val originalTimer = timerDao.getTimer(id = 2)
        val updatedTimer = originalTimer.copy(listPosition = 0)
        timerDao.update(updatedTimer)
    }

    @Test
    fun insertExistingTimer_ignored() = runBlocking {
        val timer = makeTimer()
        timerDao.insert(timer)

        val originalTimer = timerDao.getTimer(id = 1)
        val updatedTimer = originalTimer.copy(name = "New Name!")
        timerDao.insert(updatedTimer)

        assertNotEquals(updatedTimer, timerDao.getTimer(id = 1))
    }

    @Test
    fun deleteTimer_movesOtherTimersUp() = runBlocking {
        repeat(4) { timerDao.insert(makeTimer()) }
        val timers = timerDao.getTimers()

        val firstTimer = timers[0]
        timerDao.delete(firstTimer)

        val updatedTimers = timerDao.getTimers()
        assertEquals(3, updatedTimers.size)

        var listPosition = 0
        updatedTimers.forEach {
            assertEquals(listPosition, it.listPosition)
            listPosition++
        }
    }

    @Test
    fun swapTwoTimers_succeeds() = runBlocking {
        timerDao.insert(makeTimer())
        timerDao.insert(makeTimer())

        timerDao.updateTimerListPosition(0, 1)

        assertEquals(0, timerDao.getTimer(id = 2).listPosition)
        assertEquals(1, timerDao.getTimer(id = 1).listPosition)
    }

    @Test
    fun moveTimerUpInList_movesOtherTimersDown() = runBlocking {
        repeat(5) { timerDao.insert(makeTimer()) }

        var idToListPosition = mapOf(
            1 to 0,
            2 to 1,
            3 to 2,
            4 to 3,
            5 to 4, // timer to move
        )
        assertEquals(idToListPosition, timerDao.getTimers().associate { it.id to it.listPosition })

        timerDao.updateTimerListPosition(from = 4, to = 2)

        idToListPosition = mapOf(
            1 to 0,
            2 to 1,
            5 to 2, // timer that was moved
            3 to 3, // moved down in list
            4 to 4, // moved down in list
        )
        assertEquals(idToListPosition, timerDao.getTimers().associate { it.id to it.listPosition })
    }

    @Test
    fun moveTimerDownInList_movesOtherTimersUp() = runBlocking {
        repeat(5) { timerDao.insert(makeTimer()) }

        var idToListPosition = mapOf(
            1 to 0,
            2 to 1, // timer to move
            3 to 2,
            4 to 3,
            5 to 4,
        )
        assertEquals(idToListPosition, timerDao.getTimers().associate { it.id to it.listPosition })

        timerDao.updateTimerListPosition(from = 1, to = 4)

        idToListPosition = mapOf(
            1 to 0,
            3 to 1, // moved up in list
            4 to 2, // moved up in list
            5 to 3, // moved up in list
            2 to 4, // timer that was moved
        )
        assertEquals(idToListPosition, timerDao.getTimers().associate { it.id to it.listPosition })
    }

    @Test(expected = TimerDaoException::class)
    fun moveTimerToBeyondMaximumPosition_throwsException() = runBlocking {
        repeat(3) { timerDao.insert(makeTimer()) }
        timerDao.updateTimerListPosition(0, 3)
    }

    @Test(expected = TimerDaoException::class)
    fun moveTimerFromBeyondMaximumPosition_throwsException() = runBlocking {
        repeat(3) { timerDao.insert(makeTimer()) }
        timerDao.updateTimerListPosition(3, 0)
    }

    private fun makeTimer(): Timer {
        return Timer(
            name = "Test Timer",
            initialDurationMillis = 1000L,
            state = TimerState.NOT_STARTED,
            finishTime = null,
            millisUntilFinished = 1000L
        )
    }

}
