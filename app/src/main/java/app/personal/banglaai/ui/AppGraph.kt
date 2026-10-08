package app.personal.banglaai.ui

import android.content.Context
import app.personal.banglaai.doc.DocWorkflow
import app.personal.banglaai.storage.ModelStore
import app.personal.banglaai.storage.Vault

/** One vault / model store / workflow for the whole process, so a running job and the screen that controls it always talk to the same objects (pause/cancel work after a re-lock). */
object AppGraph {
    private var k: app.personal.banglaai.security.KeyVault? = null; private var v: Vault? = null; private var m: ModelStore? = null; private var w: DocWorkflow? = null
    @Synchronized fun keys(c: Context) = k ?: app.personal.banglaai.security.KeyVault(c.applicationContext).also { k = it }
    @Synchronized fun vault(c: Context): Vault = v ?: Vault(c.applicationContext, keys(c)).also { v = it }
    @Synchronized fun models(c: Context): ModelStore = m ?: ModelStore(c.applicationContext).also { m = it }
    @Synchronized fun workflow(c: Context): DocWorkflow = w ?: DocWorkflow(c.applicationContext, vault(c), models(c)).also { w = it }
    @Synchronized fun workflowIfCreated(): DocWorkflow? = w
    @Synchronized fun keysIfCreated() = k
}
