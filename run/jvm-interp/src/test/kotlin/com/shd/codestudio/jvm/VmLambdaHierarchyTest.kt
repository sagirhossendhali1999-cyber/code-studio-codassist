package com.shd.codestudio.jvm

import com.shd.codestudio.jvm.fixtures.SamHierarchy
import com.shd.codestudio.jvm.host.Registry
import com.shd.codestudio.jvm.kfixtures.samLambdaAsItsRealSuperInterface
import com.shd.codestudio.jvm.kfixtures.samLambdaAsItsSuperInterface
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A lambda (a [VmLambda]) is its functional interface AND every supertype of that interface. Regression for a
 * Compose preview that stopped at `ClassCastException: cannot cast com.shd.codestudio.jvm.VmLambda to
 * androidx/lifecycle/LifecycleObserver`: a `LifecycleEventObserver { _, e -> }` in a `DisposableEffect` is
 * coerced with `checkcast LifecycleObserver` before `lifecycle.addObserver(observer)`, and the lambda's type was
 * checked only against its exact interface. Past the cast, the same call has to hand the platform a proxy of the
 * lambda's OWN interface (a `LifecycleObserver` proxy is a marker the registry never dispatches) and the SAME
 * proxy on every crossing (`removeObserver` in the dispose block must name the object `addObserver` did).
 */
class VmLambdaHierarchyTest {

    private val vm = Vm(policy = InterpretPolicy { name ->
        !name.startsWith("com/shd/codestudio/jvm/host/") && InterpretPolicy.DEFAULT.interpret(name)
    })

    private val SAM = "com/shd/codestudio/jvm/fixtures/SamHierarchy"
    private val KFX = "com/shd/codestudio/jvm/kfixtures/KFxKt"

    @Test fun lambdaCastToASupertypeOfItsInterface() {
        assertEquals(
            SamHierarchy.castToSuper("abc"),
            vm.invokeStatic(SAM, "castToSuper", "(Ljava/lang/String;)Ljava/lang/String;", listOf("abc")),
        )
        assertEquals(1, vm.invokeStatic(SAM, "instanceChecks", "()Z"), "instanceof: own, super, and two unrelated interfaces")
    }

    @Test fun lambdaRegisteredByAMarkerSuperInterfaceIsDispatchedAndRemovable() {
        val expected = SamHierarchy.observeThenRemove(Registry())
        assertEquals("resumed;|registered=0|ignored=0", expected, "the real run is the oracle")
        assertEquals(
            expected,
            vm.invokeStatic(SAM, "observeThenRemove", "(Lcom/shd/codestudio/jvm/host/Registry;)Ljava/lang/String;", listOf(Registry())),
            "the lambda must cross as its own interface (dispatched) and as one object (removed)",
        )
    }

    @Test fun objectMethodsOnALambda() {
        assertEquals(1, vm.invokeStatic(SAM, "objectMethodsOnLambda", "()Z"))
    }

    @Test fun defaultMethodOfARealInterfaceRunsOnTheLambda() {
        assertEquals(SamHierarchy.reversedComparator(), vm.invokeStatic(SAM, "reversedComparator", "()I"))
    }

    @Test fun defaultMethodOfAnInterpretedInterfaceRunsWithTheLambdaAsThis() {
        assertEquals(
            SamHierarchy.defaultOnInterpretedInterface(),
            vm.invokeStatic(SAM, "defaultOnInterpretedInterface", "()Ljava/lang/String;"),
        )
    }

    @Test fun kotlinFunInterfaceLambdaIsItsSuperInterfaces() {
        assertEquals(samLambdaAsItsSuperInterface(), vm.invokeStatic(KFX, "samLambdaAsItsSuperInterface", "()Ljava/lang/String;"))
        assertEquals(samLambdaAsItsRealSuperInterface(), vm.invokeStatic(KFX, "samLambdaAsItsRealSuperInterface", "()Ljava/lang/String;"))
    }
}
