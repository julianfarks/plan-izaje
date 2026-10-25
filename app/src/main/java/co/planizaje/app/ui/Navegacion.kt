package co.planizaje.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import co.planizaje.app.ui.accesorios.EditorAccesorio
import co.planizaje.app.ui.accesorios.PantallaAccesorios
import co.planizaje.app.ui.asistente.PantallaAsistente
import co.planizaje.app.ui.copias.PantallaCopias
import co.planizaje.app.ui.croquis.PantallaCroquis
import co.planizaje.app.ui.gruas.EditorGrua
import co.planizaje.app.ui.gruas.EditorTabla
import co.planizaje.app.ui.gruas.PantallaGruas
import co.planizaje.app.ui.inicio.PantallaInicio
import co.planizaje.app.ui.pdf.PantallaPlanes
import co.planizaje.app.ui.proyectos.PantallaProyectos

object Rutas {
    const val INICIO = "inicio"
    const val PROYECTOS = "proyectos"
    const val GRUAS = "gruas"
    const val ACCESORIOS = "accesorios"
    const val PLANES = "planes"
    const val COPIAS = "copias"
    fun asistente(id: String, paso: Int = 0) = "asistente/$id?paso=$paso"
    fun croquis(id: String) = "croquis/$id"
    fun grua(id: String) = "grua/$id"
    fun tabla(gruaId: String, tablaId: String) = "tabla/$gruaId/$tablaId"
    fun accesorio(id: String) = "accesorio/$id"
}

@Composable
fun Navegacion(nav: NavHostController = rememberNavController()) {
    val atras: () -> Unit = { if (!nav.popBackStack()) nav.navigate(Rutas.INICIO) }
    NavHost(nav, startDestination = Rutas.INICIO) {
        composable(Rutas.INICIO) { PantallaInicio(nav) }
        composable(Rutas.PROYECTOS) { PantallaProyectos(nav, atras) }
        composable(
            "asistente/{id}?paso={paso}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }, navArgument("paso") { type = NavType.IntType; defaultValue = 0 }),
        ) { b -> PantallaAsistente(nav, b.arguments!!.getString("id")!!, b.arguments!!.getInt("paso"), atras) }
        composable("croquis/{id}") { b -> PantallaCroquis(b.arguments!!.getString("id")!!, atras) }
        composable(Rutas.GRUAS) { PantallaGruas(nav, atras) }
        composable("grua/{id}") { b -> EditorGrua(nav, b.arguments!!.getString("id")!!, atras) }
        composable("tabla/{g}/{t}") { b -> EditorTabla(b.arguments!!.getString("g")!!, b.arguments!!.getString("t")!!, atras) }
        composable(Rutas.ACCESORIOS) { PantallaAccesorios(nav, atras) }
        composable("accesorio/{id}") { b -> EditorAccesorio(b.arguments!!.getString("id")!!, atras) }
        composable(Rutas.PLANES) { PantallaPlanes(nav, atras) }
        composable(Rutas.COPIAS) { PantallaCopias(atras) }
    }
}
