package com.difrancescogianmarco.arcore_flutter_plugin

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import com.difrancescogianmarco.arcore_flutter_plugin.flutter_models.FlutterArCoreNode
import com.difrancescogianmarco.arcore_flutter_plugin.utils.ArCoreUtils
import com.difrancescogianmarco.arcore_flutter_plugin.utils.DecodableUtils.Companion.parseVector3
import com.google.ar.core.Config
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Quaternion
import com.gorisse.thomas.sceneform.light.LightEstimationConfig
import com.gorisse.thomas.sceneform.lightEstimationConfig
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView
import java.util.concurrent.CompletableFuture

open class BaseArCoreView(
    var activity: Activity?,
    context: Context?,
    messenger: BinaryMessenger,
    id: Int,
    protected val debug: Boolean
) : PlatformView, MethodChannel.MethodCallHandler {

    var activityLifecycleCallbacks: Application.ActivityLifecycleCallbacks? = null
    protected var methodChannel: MethodChannel? =
        MethodChannel(messenger, "arcore_flutter_plugin_$id")
    protected var arSceneView: ArSceneView? = null
    protected val RC_PERMISSIONS = 0x123
    protected var installRequested: Boolean = false
    private val TAG: String = BaseArCoreView::class.java.name
    protected var isSupportedDevice = false
    protected val allFlutterNodesSet = mutableSetOf<FlutterArCoreNode>()

    init {
        debugLog("base init")
        methodChannel!!.setMethodCallHandler(this)
        if (ArCoreUtils.checkIsSupportedDeviceOrFinish(activity!!)) {
            isSupportedDevice = true
            arSceneView = ArSceneView(context)
            arSceneView?.lightEstimationConfig =
                LightEstimationConfig(mode = Config.LightEstimationMode.DISABLED)
            ArCoreUtils.requestCameraPermission(activity!!, RC_PERMISSIONS)
            setupLifeCycle(activity!!)
        }
    }

    protected fun debugLog(message: String) {
        if (debug) {
            Log.i(TAG, message)
        }
    }

    private fun setupLifeCycle(activity: Activity) {
        activityLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                debugLog("onActivityCreated")
            }

            override fun onActivityStarted(activity: Activity) {
                debugLog("onActivityStarted")
            }

            override fun onActivityResumed(activity: Activity) {
                debugLog("onActivityResumed")
                onResume()
            }

            override fun onActivityPaused(activity: Activity) {
                debugLog("onActivityPaused")
                onPause()
            }

            override fun onActivityStopped(activity: Activity) {
                debugLog("onActivityStopped")
                onPause()
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {
                debugLog("onActivityDestroyed")
//                onDestroy()
            }
        }

        activity.application
            .registerActivityLifecycleCallbacks(this.activityLifecycleCallbacks)
    }

    override fun getView(): View {
        return arSceneView as View
    }

    override fun dispose() {
        if (arSceneView != null) {
            onPause()
            onDestroy()
        }
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {

    }

    fun attachNodeToParent(node: Node?, parentNodeName: String?) {
        if (parentNodeName != null) {
            val parentNode: Node? = arSceneView?.scene?.findByName(parentNodeName)
            parentNode?.addChild(node)
        } else {
            arSceneView?.scene?.addChild(node)
        }
    }

    fun onAddNode(flutterArCoreNode: FlutterArCoreNode, result: MethodChannel.Result?) {
        debugLog(flutterArCoreNode.toString())
        NodeFactory.makeNode(
            activity!!.applicationContext,
            flutterArCoreNode,
            debug
        ) { node, throwable ->
            debugLog("inserted ${node?.name}")
            if (node != null) {
                allFlutterNodesSet.add(flutterArCoreNode)
                attachNodeToParent(node, flutterArCoreNode.parentNodeName)
                for (n in flutterArCoreNode.children) {
                    n.parentNodeName = flutterArCoreNode.name
                    onAddNode(n, null)
                }
                result?.success(null)
            } else if (throwable != null) {
                result?.error("onAddNode", throwable.localizedMessage, null)
            }
        }
    }

    fun moveNodeTo(name: String, position: HashMap<String, *>) {
        val node = arSceneView?.scene?.findByName(name)
        if (node != null) {
            val value = parseVector3(position)
            node.localPosition = value
        }
    }

    fun scaleNodeTo(name: String, scale: HashMap<String, *>) {
        val node = arSceneView?.scene?.findByName(name)
        if (node != null) {
            val value = parseVector3(scale)
            node.localScale = value
        }
    }

    fun rotateNodeTo(name: String, rotation: HashMap<String, *>) {
        val node = arSceneView?.scene?.findByName(name)
        if (node != null) {
            val value = parseVector3(rotation)
            node.localRotation = Quaternion.eulerAngles(value)
        }
    }

    fun removeNode(name: String, result: MethodChannel.Result?) {
        val node = arSceneView?.scene?.findByName(name)
        if (node != null) {
            removeNode(node)
        }
        result?.success(null)
    }

    fun removeNode(node: Node) {
        arSceneView?.scene?.children?.forEach {
            debugLog("child: ${it.name}, $it")
        }

        if (node is AnchorNode) {
            node.anchor?.detach()
        }
        while (true) {
            val child = node.children?.firstOrNull() ?: break
            child.renderable = null
            node.removeChild(child)
        }
        arSceneView?.scene?.removeChild(node)
        node.renderable = null

        var flutterNodeToRemove: FlutterArCoreNode? = null
        for (flutterNode in allFlutterNodesSet) {
            if (flutterNode.name == node.name) {
                debugLog("remove flutter node")
                flutterNode.dispose()
                flutterNodeToRemove = flutterNode
                break
            }
        }
        if (flutterNodeToRemove != null) {
            allFlutterNodesSet.remove(flutterNodeToRemove)
        }
        debugLog("node removed ${node.name} > ${allFlutterNodesSet.size} > $node}")

        System.gc()
    }

    fun onPause() {
        debugLog("onPause()")
        if (arSceneView != null) {
            arSceneView?.pause()
            allFlutterNodesSet.forEach {
                it.pause()
            }
        }
    }

    open fun onResume() {}

    open fun cleanup() {
        arSceneView?.pause()
        for (node in allFlutterNodesSet) {
            node.dispose()
        }
        allFlutterNodesSet.clear()
//        ArSceneView.destroyAllResources()
        System.gc()
    }

    open fun onDestroy() {
//        arSceneView?.pause()
//        CompletableFuture.runAsync {
            arSceneView?.destroy()
            arSceneView = null
            activity = null
            activityLifecycleCallbacks = null
            methodChannel = null
//            System.gc()
            Log.d(
                TAG,
                "arcore-view destroy, free-mem: ${
                    Runtime.getRuntime().freeMemory() / 1024.0 / 1024
                } mb, used-mem: ${
                    Runtime.getRuntime().totalMemory() / 1024.0 / 1024
                } mb, max-mem: ${Runtime.getRuntime().maxMemory() / 1024.0 / 1024} mb"
            )
//        ArSceneView.destroyAllResources()
//        }
    }
}
