/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
 * See the project homepage at: https://monix.io
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

package monix.execution.schedulers

import minitest.TestSuite
import monix.execution.Scheduler
import monix.execution.schedulers.ExecutionModel.AlwaysAsyncExecution

import scala.concurrent.Promise

object TrampolineSchedulerSuite extends TestSuite[(Scheduler, TestScheduler)] {
  def setup(): (Scheduler, TestScheduler) = {
    val u = TestScheduler(ExecutionModel.Default)
    val t = TrampolineScheduler(u, ExecutionModel.Default)
    (t, u)
  }

  def tearDown(env: (Scheduler, TestScheduler)): Unit = {
    assert(env._2.state.tasks.isEmpty, "tasks.isEmpty")
  }

  test("execute async should execute immediately") { case (s, _) =>
    var effect = 0
    val p = Promise[Int]()

    s.executeAsync {
      effect += 1
      s.executeAsync {
        effect += 2
        s.executeAsync {
          effect += 3
          p.success(effect)
        }
      }
    }

    // Should already be executed
    assertEquals(effect, 1 + 2 + 3)
  }

  test("execute local should work") { case (s, _) =>
    var effect = 0

    s.executeTrampolined {
      effect += 1
      s.executeTrampolined {
        effect += 2
        s.executeTrampolined {
          effect += 3
        }
      }
    }

    assertEquals(effect, 1 + 2 + 3)
  }

  test("schedule for execution with delay") { case (s, u) =>
    import concurrent.duration._
    val p = Promise[Unit]()
    val startAt = s.currentTimeMillis()
    s.scheduleOnce(100.millis)(p.success(()))

    u.tick(100.millis)
    val duration = s.currentTimeMillis() - startAt
    assert(duration >= 100, "duration >= 100")
    assert(p.future.isCompleted, "p.future.isCompleted")
  }

  test("report failure should work") { case (s, u) =>
    val ex = new RuntimeException("dummy")
    s.reportFailure(ex)
    assertEquals(u.state.lastReportedError, ex)
  }

  test("scheduleWithFixedDelay") { case (s,u) =>
    import concurrent.duration._
    var effect = 0
    val task = s.scheduleWithFixedDelay(1.second, 1.second) { effect += 1 }

    u.tick()
    assertEquals(effect, 0)
    u.tick(1.second)
    assertEquals(effect, 1)
    u.tick(1.second)
    assertEquals(effect, 2)
    task.cancel()
    u.tick(1.second)
    assertEquals(effect, 2)
  }

  test("scheduleAtFixedRate") { case (s,u) =>
    import concurrent.duration._
    var effect = 0
    val task = s.scheduleAtFixedRate(1.second, 1.second) { effect += 1 }

    u.tick()
    assertEquals(effect, 0)
    u.tick(1.second)
    assertEquals(effect, 1)
    u.tick(1.second)
    assertEquals(effect, 2)
    task.cancel()
    u.tick(1.second)
    assertEquals(effect, 2)
  }

  test("withExecutionModel") { case (s,_) =>
    val em = AlwaysAsyncExecution
    val s2 = s.withExecutionModel(em)

    assert(s2.isInstanceOf[TrampolineScheduler], "s2.isInstanceOf[TrampolineScheduler]")
    assertEquals(s2.executionModel, em)
  }
}