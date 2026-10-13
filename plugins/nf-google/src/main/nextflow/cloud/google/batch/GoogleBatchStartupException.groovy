/*
 * Copyright 2013-2026, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nextflow.cloud.google.batch

import com.google.cloud.batch.v1.JobStatus
import com.google.cloud.batch.v1.TaskStatus
import nextflow.exception.ProcessException
import java.time.Instant

/** A confirmed Batch agent timeout before task execution. */
class GoogleBatchStartupException extends ProcessException {
    GoogleBatchStartupException(String message) { super(message) }

    static GoogleBatchStartupException fromStatus(String jobId, JobStatus job, TaskStatus task) {
        if (job == null || task == null || job.state != JobStatus.State.FAILED || task.state != TaskStatus.State.PENDING)
            return null
        if (task.statusEventsList.any { it.hasTaskExecution() || (it.type == 'STATUS_CHANGED' && it.description.contains('RUNNING')) })
            return null
        if (job.statusEventsList.any { it.type == 'STATUS_CHANGED' && it.description.contains('RUNNING') })
            return null
        final event = job.statusEventsList.find {
            it.description =~ /(?i)no VM has agent reporting correctly within (?:the )?time window \d+ seconds/
        }
        if (event == null)
            return null
        final time = event.hasEventTime() ? Instant.ofEpochSecond(event.eventTime.seconds, event.eventTime.nanos).toString() : 'unknown'
        return new GoogleBatchStartupException("Batch job ${jobId} failed before task execution at ${time}: ${event.description}")
    }
}
