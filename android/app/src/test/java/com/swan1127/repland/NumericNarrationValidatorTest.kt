package com.swan1127.repland

import com.swan1127.repland.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class NumericNarrationValidatorTest {
    private val p=NumericParameter("estimate:COURSE","估时","倍",1.5,count=3,availability=NumericAvailability.USABLE)
    private val request=NumericNarrationRequest("request","snapshot",NumericProfileCalculator.RULE,"2026-07-13","2026-10-10",listOf(p))
    private val response=NumericNarration("request","snapshot",listOf(NumericClaim(p.id,1.5)),NumericNextStep.REVIEW_ESTIMATE)
    @Test fun exact_snapshot_facts_are_accepted() { assertTrue(NumericNarrationValidator.valid(request,response)) }
    @Test fun stale_versions_ids_values_duplicates_missing_and_nonfinite_are_rejected() {
        val invalid=listOf(response.copy(requestId="old"),response.copy(profileVersion="old"),response.copy(claims=emptyList()),
            response.copy(claims=response.claims+response.claims),response.copy(claims=listOf(NumericClaim("ability",1.5))),
            response.copy(claims=listOf(NumericClaim(p.id,1.6))),response.copy(claims=listOf(NumericClaim(p.id,Double.NaN))))
        assertTrue(invalid.all { !NumericNarrationValidator.valid(request,it) })
    }
    @Test fun reference_only_can_describe_facts_but_cannot_recommend_estimation_or_splitting() {
        val r=request.copy(parameters=listOf(p.copy(availability=NumericAvailability.REFERENCE)))
        assertFalse(NumericNarrationValidator.valid(r,response))
        assertTrue(NumericNarrationValidator.valid(r,response.copy(nextStep=NumericNextStep.NONE)))
        assertFalse(NumericNarrationValidator.valid(request,response.copy(nextStep=NumericNextStep.CONSIDER_SPLITTING)))
    }
}
