package com.spx.express.ui.customer

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter

data class CityDetails(val postal: String, val barangays: List<String>)

class NoFilterArrayAdapter<T>(
    context: Context,
    resource: Int,
    private val items: List<T>
) : ArrayAdapter<T>(context, resource, items) {

    private val filter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val results = FilterResults()
            results.values = items
            results.count = items.size
            return results
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            notifyDataSetChanged()
        }
    }

    override fun getFilter(): Filter {
        return filter
    }
}

object LocationHelper {
    val locationsMap = mapOf(
        "Metro Manila" to mapOf(
            "Manila" to CityDetails("1000", listOf("Intramuros", "Binondo", "Ermita", "Malate", "Paco", "Pandacan", "Sampaloc", "Tondo")),
            "Quezon City" to CityDetails("1100", listOf("Diliman", "Commonwealth", "Batasan Hills", "Cubao", "Katipunan", "Kamuning", "Project 4", "Project 8")),
            "Makati City" to CityDetails("1200", listOf("Bel-Air", "Poblacion", "Urdaneta", "San Lorenzo", "Bangkal", "Guadalupe Nuevo", "Pembo")),
            "Taguig City" to CityDetails("1630", listOf("Fort Bonifacio", "Western Bicutan", "Ususan", "Signal Village", "Pinagsama", "Napindan")),
            "Pasig City" to CityDetails("1600", listOf("Kapitolyo", "Ortigas Center", "San Joaquin", "Caniogan", "Rosario", "Manggahan"))
        ),
        "Cebu" to mapOf(
            "Cebu City" to CityDetails("6000", listOf("Lahug", "Mabolo", "Banilad", "Guadalupe", "Capitol Site", "Talamban", "Pardo", "Labangon")),
            "Mandaue City" to CityDetails("6014", listOf("Subangdaku", "Centro", "Tipolo", "Bakilid", "Cabancalan", "Looc", "Guizo")),
            "Lapu-Lapu City" to CityDetails("6015", listOf("Basak", "Maribago", "Mactan", "Pajo", "Punta Engaño", "Babag")),
            "Talisay City" to CityDetails("6045", listOf("Tabunok", "Bulacao", "Lawaan", "Poblacion", "Dumlog", "Cansojong"))
        ),
        "Davao del Sur" to mapOf(
            "Davao City" to CityDetails("8000", listOf("Agdao", "Bajada", "Buhangin", "Talomo", "Matina", "Ma-a", "Toril", "Poblacion")),
            "Digos City" to CityDetails("8002", listOf("Tres de Mayo", "Matti", "Aplaya", "San Jose", "Poblacion")),
            "Santa Cruz" to CityDetails("8001", listOf("Bato", "Tuban", "Astorga", "Zone I", "Coronon"))
        ),
        "Leyte" to mapOf(
            "Tacloban City" to CityDetails("6500", listOf("Abucay", "Downtown", "Marasbaras", "San Jose", "Utap", "Kawayan", "Bagacay")),
            "Ormoc City" to CityDetails("6541", listOf("Cogon", "Liloan", "Punta", "Valencia", "San Isidro", "Tambulilid")),
            "Baybay City" to CityDetails("6521", listOf("Panggasugan", "Poblacion Zone 1", "Guadalupe", "Caridad", "San Agustin"))
        ),
        "Iloilo" to mapOf(
            "Iloilo City" to CityDetails("5000", listOf("Jaro", "La Paz", "Mandurriao", "Molo", "Arevalo", "Lapuz", "City Proper")),
            "Passi City" to CityDetails("5037", listOf("Poblacion Ilawod", "Poblacion Ilaya", "Man-it", "Salngan", "Araneta")),
            "Oton" to CityDetails("5020", listOf("Poblacion East", "Poblacion West", "Trapiche", "San Antonio", "Cagbang"))
        ),
        "Benguet" to mapOf(
            "Baguio City" to CityDetails("2600", listOf("Camp John Hay", "Magsaysay", "Session Road", "Trancoville", "Loakan", "Bakakeng", "Irisan", "Asin Road")),
            "La Trinidad" to CityDetails("2601", listOf("Pico", "Balili", "Puguis", "Betag", "Cruz", "Wangal")),
            "Itogon" to CityDetails("2604", listOf("Poblacion", "Ucab", "Ampucao", "Gumatdang", "Tuding"))
        ),
        "Batangas" to mapOf(
            "Batangas City" to CityDetails("4200", listOf("Alangilan", "Bolbok", "Calicanto", "Kumintang Ibaba", "Kumintang Ilaya", "Libjo", "Pallocan West", "Tingga Itaas")),
            "Lipa City" to CityDetails("4217", listOf("Antipolo", "Balintawak", "Marawoy", "Sabang", "Dagatan", "Tambo", "Lumbang")),
            "Tanauan City" to CityDetails("4232", listOf("Darasa", "Poblacion Barangay 1", "Poblacion Barangay 4", "Sambal", "Pagaspas")),
            "Nasugbu" to CityDetails("4231", listOf("Wawa", "Lumbangan", "Poblacion Stage 1", "Bilaran", "Calayo"))
        ),
        "Cavite" to mapOf(
            "Imus" to CityDetails("4103", listOf("Anabu I", "Anabu II", "Bayan Luma", "Bucandala", "Toclong", "Carsadang Bago", "Malagasang I", "Malagasang II")),
            "Dasmariñas" to CityDetails("4114", listOf("Burol", "Langkaan", "Sampaloc", "Salawag", "Paliparan I", "Paliparan III", "San Jose", "Sabang")),
            "Bacoor City" to CityDetails("4102", listOf("Molino I", "Molino III", "Molino IV", "San Nicolas", "Habay", "Panapaan", "Mambog")),
            "Tagaytay City" to CityDetails("4120", listOf("Mendez Crossing East", "Silang Junction North", "Maharlika East", "Sungay East", "Iruhin"))
        ),
        "Laguna" to mapOf(
            "Calamba" to CityDetails("4027", listOf("Bucal", "Halang", "Pansol", "Real", "Crossing", "Mayapa", "Canlubang", "Looc")),
            "Santa Rosa" to CityDetails("4026", listOf("Balibago", "Don Jose", "Macabling", "Tagapo", "Dila", "Pulong Santa Cruz", "Malitlit")),
            "Biñan City" to CityDetails("4024", listOf("San Francisco", "San Jose", "San Vicente", "Platero", "Santo Tomas", "Mamplasan")),
            "San Pedro City" to CityDetails("4025", listOf("Pagsanjan", "Landayan", "San Vicente", "Pacita Complex", "Nueva", "United Bayanihan"))
        ),
        "Pampanga" to mapOf(
            "San Fernando" to CityDetails("2000", listOf("Calulut", "Dolores", "San Jose", "Sindalan", "Telebastagan", "St. Jude", "Maimpis", "Quebiawan")),
            "Angeles City" to CityDetails("2009", listOf("Balibago", "Cutcut", "Malabanias", "Pulung Maragul", "Pandan", "Santo Domingo", "Anunas")),
            "Mabalacat City" to CityDetails("2010", listOf("Dau", "San Francisco", "Mamatitang", "Camachiles", "Mabiga", "Duquit")),
            "Guagua" to CityDetails("2003", listOf("San Pedro", "San Nicolas", "San Roque", "Bancal", "Pulungmasle"))
        ),
        "Pangasinan" to mapOf(
            "Dagupan City" to CityDetails("2400", listOf("Bonuan Gueset", "Caranglaan", "Malued", "Tapuac", "Bonuan Boquig", "Bonuan Binloc", "Pantal", "Poblacion Oeste")),
            "Urdaneta City" to CityDetails("2428", listOf("Anonas", "Camantiles", "Nancayasan", "Poblacion", "Pinmaludpod", "San Vicente", "Bactad East")),
            "San Carlos City" to CityDetails("2420", listOf("Palaris", "Roxas Boulevard", "Tandoc", "Balaya", "Maniago", "Taloy")),
            "Alaminos City" to CityDetails("2404", listOf("Lucap", "Poblacion", "Bolo", "Pangapisan", "San Vicente"))
        ),
        "Misamis Oriental" to mapOf(
            "Cagayan de Oro" to CityDetails("9000", listOf("Carmen", "Kauswagan", "Lapasan", "Nazareth", "Macasandig", "Balulang", "Patag", "Bulua")),
            "Gingoog City" to CityDetails("9014", listOf("Poblacion Barangay 1", "Poblacion Barangay 20", "San Jose", "Lunao", "Anakan")),
            "El Salvador City" to CityDetails("9017", listOf("Poblacion", "Amoros", "Cogon", "Sinaloc", "Taytay"))
        )
    )
}
