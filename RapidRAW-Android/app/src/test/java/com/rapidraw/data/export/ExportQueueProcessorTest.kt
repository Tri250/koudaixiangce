package com.rapidraw.data.export

import com.rapidraw.data.model.ExportJob
import com.rapidraw.data.model.ExportJobStatus
import com.rapidraw.data.repository.ExportQueueRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ExportQueueProcessorTest {

    @Before
    fun setUp() {
        // Reset queue state before each test
        ExportQueueRepository.clear()
    }

    @Test
    fun `ExportQueueProcessor is a singleton`() {
        // ExportQueueProcessor is an object (Kotlin singleton)
        val instance = ExportQueueProcessor
        assertNotNull("ExportQueueProcessor should be a singleton object", instance)
    }

    @Test
    fun `queue size starts at 0`() {
        val jobs = ExportQueueRepository.jobs.value
        assertTrue("Queue should start empty", jobs.isEmpty())
        assertEquals("Queue size should be 0", 0, jobs.size)
    }

    @Test
    fun `addJob adds a job to the queue`() {
        val job = ExportJob(
            id = "test-job-1",
            imagePath = "/test/path/image.raw",
            status = ExportJobStatus.QUEUED,
            progress = 0f,
        )

        ExportQueueRepository.addJob(job)

        val jobs = ExportQueueRepository.jobs.value
        assertEquals("Queue should have 1 job", 1, jobs.size)
        assertEquals("Job ID should match", "test-job-1", jobs[0].id)
    }

    @Test
    fun `removeJob on non-existent job does not crash`() {
        ExportQueueRepository.removeJob("non-existent-job-id")
        // Should not throw any exception
        assertEquals("Queue should still be empty", 0, ExportQueueRepository.jobs.value.size)
    }

    @Test
    fun `clear removes all jobs`() {
        val job1 = ExportJob(id = "job-1", imagePath = "/path/1", status = ExportJobStatus.QUEUED, progress = 0f)
        val job2 = ExportJob(id = "job-2", imagePath = "/path/2", status = ExportJobStatus.QUEUED, progress = 0f)

        ExportQueueRepository.addJob(job1)
        ExportQueueRepository.addJob(job2)
        assertEquals("Queue should have 2 jobs", 2, ExportQueueRepository.jobs.value.size)

        ExportQueueRepository.clear()
        assertEquals("Queue should be empty after clear", 0, ExportQueueRepository.jobs.value.size)
    }

    @Test
    fun `updateJobStatus modifies job state`() {
        val job = ExportJob(id = "job-update", imagePath = "/path/img", status = ExportJobStatus.QUEUED, progress = 0f)
        ExportQueueRepository.addJob(job)

        ExportQueueRepository.updateJobStatus("job-update", ExportJobStatus.EXPORTING, progress = 0.5f)

        val updated = ExportQueueRepository.jobs.value.first { it.id == "job-update" }
        assertEquals("Status should be EXPORTING", ExportJobStatus.EXPORTING, updated.status)
        assertEquals("Progress should be 0.5", 0.5f, updated.progress)
    }
}