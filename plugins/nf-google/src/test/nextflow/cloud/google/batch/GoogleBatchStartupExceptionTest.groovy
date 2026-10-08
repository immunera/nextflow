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
import com.google.cloud.batch.v1.StatusEvent
import com.google.cloud.batch.v1.TaskExecution
import spock.lang.Specification

class GoogleBatchStartupExceptionTest extends Specification {
    def 'classifies only proven startup failures and retains the timeout event'() {
        given:
        def timeout = StatusEvent.newBuilder().setDescription('no VM has agent reporting correctly within the time window 1080 seconds').build()
        def builder = JobStatus.newBuilder().setState(JobStatus.State.FAILED).addStatusEvents(timeout)
        builder.addStatusEvents(StatusEvent.newBuilder().setDescription('generic final failure'))
        if (running) builder.addStatusEvents(StatusEvent.newBuilder().setType('STATUS_CHANGED').setDescription('Job state is set from SCHEDULED to RUNNING for job'))
        def task = TaskStatus.newBuilder().setState(state)
        if (executed) task.addStatusEvents(StatusEvent.newBuilder().setTaskExecution(TaskExecution.newBuilder().setExitCode(1)))

        when:
        def error = GoogleBatchStartupException.fromStatus('job1', builder.build(), task.build())

        then:
        (error != null) == expected
        if (expected) assert error.message.contains('job1') && error.message.contains('1080 seconds')

        where:
        state                    | executed | running | expected
        TaskStatus.State.PENDING | false    | false   | true
        TaskStatus.State.RUNNING | false    | false   | false
        TaskStatus.State.PENDING | true     | false   | false
        TaskStatus.State.PENDING | false    | true    | false
    }

    def 'missing evidence and unexplained failures remain unclassified'() {
        expect:
        GoogleBatchStartupException.fromStatus('job', null, null) == null
        GoogleBatchStartupException.fromStatus('job', JobStatus.newBuilder().setState(JobStatus.State.FAILED).build(), TaskStatus.newBuilder().setState(TaskStatus.State.PENDING).build()) == null
    }
    def 'prior task execution state blocks startup classification'() {
        given:
        def job = JobStatus.newBuilder().setState(JobStatus.State.FAILED)
            .addStatusEvents(StatusEvent.newBuilder().setDescription('no VM has agent reporting correctly within the time window 1080 seconds')).build()
        def task = TaskStatus.newBuilder().setState(TaskStatus.State.PENDING)
            .addStatusEvents(StatusEvent.newBuilder().setType('STATUS_CHANGED').setDescription('Task state changed to RUNNING.')).build()
        expect:
        GoogleBatchStartupException.fromStatus('job', job, task) == null
    }

}
