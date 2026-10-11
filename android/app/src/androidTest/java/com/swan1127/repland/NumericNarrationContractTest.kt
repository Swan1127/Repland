package com.swan1127.repland

import com.swan1127.repland.data.ai.CompatibleNumericProfileAdvisor
import com.swan1127.repland.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class NumericNarrationContractTest {
    @Test fun same_isolated_evidence_and_parameter_payload_preserve_facts_and_record_actual_sizes() {
        val samples=(0 until 12).map { i ->
            val pair=listOf(10 to 20,20 to 30,40 to 40)[i%3]
            NumericEstimateSample("private-fixture-$i",TaskCategory.COURSE,pair.first,pair.second,true)
        }
        val parameters=NumericProfileCalculator.estimates(samples,emptySet(),emptySet()).filter { it.value!=null }
        assertEquals(1.5,parameters.single { it.id=="estimate:COURSE" }.value!!,0.001)
        val request=NumericNarrationRequest("same-fixture","fixture-version",NumericProfileCalculator.RULE,"2026-07-13","2026-10-10",parameters)
        val summary=CompatibleNumericProfileAdvisor.payload(request)
        val evidence=org.json.JSONObject().put("windowStart",request.windowStart).put("windowEnd",request.windowEnd)
            .put("completedTasks",org.json.JSONArray(samples.map { s -> org.json.JSONObject()
                .put("taskId",s.taskId).put("categoryAtStart",s.category.name).put("originalMinutes",s.originalMinutes)
                .put("actualTotalMinutes",s.actualTotalMinutes).put("totalSource","USER_BACKFILL_TOTAL")
                .put("completed",true).put("originalSource","USER_INPUT").put("logReference","private-log-${s.taskId}") }))
        val summaryBytes=summary.toString().toByteArray(Charsets.UTF_8).size
        val evidenceBytes=evidence.toString().toByteArray(Charsets.UTF_8).size
        assertTrue(summaryBytes<evidenceBytes)
        assertFalse(summary.toString().contains("private"))
        val response=NumericNarration(request.requestId,request.profileVersion,parameters.map { NumericClaim(it.id,it.value!!) },NumericNextStep.REVIEW_ESTIMATE)
        assertTrue(NumericNarrationValidator.valid(request,response))
        val result=org.json.JSONObject().put("fixtureTasks",samples.size).put("fullNecessaryEvidenceBytes",evidenceBytes)
            .put("parameterPayloadBytes",summaryBytes).put("courseMedian",1.5).put("factsMatch",true)
            .put("scope","isolated synthetic fixture; no real model request or token/cost claim")
        val context=androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        java.io.File(context.getExternalFilesDir(null),"numeric-payload-comparison.json").writeText(result.toString(2))
    }
    @Test fun minimal_payload_excludes_task_prose_raw_sources_and_disabled_parameters_and_strict_reply_rejects_prose() {
        val request=NumericNarrationRequest("r","v",NumericProfileCalculator.RULE,"2026-07-13","2026-10-10",listOf(
            NumericParameter("rounds","private task title","分钟",20.0,count=3,sources=listOf("private-log-id"),availability=NumericAvailability.USABLE),
            NumericParameter("estimate:COURSE","hidden","倍",1.5,count=3,availability=NumericAvailability.DISABLED)))
        val payload=CompatibleNumericProfileAdvisor.payload(request).toString()
        assertFalse(payload.contains("private")); assertFalse(payload.contains("estimate:COURSE")); assertFalse(payload.contains("sources"))
        val raw="""{"requestId":"r","profileVersion":"v","claims":[{"parameterId":"rounds","value":20}],"nextStep":"CONSIDER_SPLITTING"}"""
        assertTrue(NumericNarrationValidator.valid(request,CompatibleNumericProfileAdvisor.decode(raw)))
        assertTrue(runCatching { CompatibleNumericProfileAdvisor.decode(raw.dropLast(1)+",\"rating\":80}") }.isFailure)
        assertTrue(runCatching { CompatibleNumericProfileAdvisor.decode(raw.replace("CONSIDER_SPLITTING","SELF_DISCIPLINE")) }.isFailure)
        assertTrue(payload.toByteArray().size < (1..50).joinToString { "private task history with raw execution evidence $it" }.toByteArray().size)
    }
}
