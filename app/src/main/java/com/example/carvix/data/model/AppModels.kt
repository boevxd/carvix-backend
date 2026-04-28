package com.example.carvix.data.model

import com.google.gson.annotations.SerializedName

data class Zayavka(
    val id: Int,
    @SerializedName("data_sozdaniya") val dataSozdaniya: String?,
    @SerializedName("sozdatel_id") val sozdatelId: Int?,
    @SerializedName("ts_id") val tsId: Int?,
    @SerializedName("tip_remonta_id") val tipRemontaId: Int?,
    val opisanie: String?,
    @SerializedName("status_id") val statusId: Int?,
    val prioritet: Int?,
    @SerializedName("status_name") val statusName: String?,
    @SerializedName("tip_remonta_name") val tipRemontaName: String?,
    @SerializedName("gos_nomer") val gosNomer: String?,
    @SerializedName("invent_nomer") val inventNomer: String?,
    @SerializedName("tekuschee_sostoyanie") val tekuscheeSostoyanie: String?,
    @SerializedName("model_name") val modelName: String?,
    @SerializedName("marka_name") val markaName: String?,
    @SerializedName("sozdatel_fio") val sozdatelFio: String?,
    val probeg: Int? = null
)

data class Remont(
    val id: Int,
    @SerializedName("zayavka_id") val zayavkaId: Int,
    @SerializedName("data_nachala") val dataNachala: String?,
    @SerializedName("data_okonchaniya") val dataOkonchaniya: String?,
    @SerializedName("mekhanik_id") val mekhanikId: Int?,
    @SerializedName("glavniy_mekhanik_id") val glavniyMekhanikId: Int?,
    @SerializedName("stoimost_rabot") val stoimostRabot: String?,
    @SerializedName("stoimost_zapchastey") val stoimostZapchastey: String?,
    val kommentariy: String?,
    val itog: String?,
    @SerializedName("mekhanik_fio") val mekhanikFio: String?,
    @SerializedName("glavniy_mekhanik_fio") val glavniyMekhanikFio: String?
)

data class ZayavkaDetails(
    val zayavka: Zayavka,
    val remont: Remont?
)

data class TS(
    val id: Int,
    @SerializedName("gos_nomer") val gosNomer: String?,
    @SerializedName("invent_nomer") val inventNomer: String?,
    @SerializedName("model_id") val modelId: Int?,
    @SerializedName("podrazdelenie_id") val podrazdelenieId: Int?,
    val probeg: Int?,
    @SerializedName("data_vypuska") val dataVypuska: String?,
    @SerializedName("tekuschee_sostoyanie") val tekuscheeSostoyanie: String?,
    @SerializedName("model_name") val modelName: String?,
    @SerializedName("marka_name") val markaName: String?,
    @SerializedName("podrazdelenie_name") val podrazdelenieName: String?
)

data class Sotrudnik(
    val id: Int,
    val fio: String,
    val login: String?,
    @SerializedName("rol_id") val rolId: Int?,
    @SerializedName("podrazdelenie_id") val podrazdelenieId: Int?,
    @SerializedName("rol_name") val rolName: String?,
    @SerializedName("podrazdelenie_name") val podrazdelenieName: String?
)

data class MekhanikActive(
    val id: Int,
    val fio: String,
    val login: String?,
    @SerializedName("active_remonts") val activeRemonts: Int,
    @SerializedName("active_zayavki") val activeZayavki: List<ActiveZayavkaInfo>?
)

data class ActiveZayavkaInfo(
    @SerializedName("zayavka_id") val zayavkaId: Int,
    val opisanie: String?,
    @SerializedName("gos_nomer") val gosNomer: String?
)

data class FeedbackMsg(
    val id: Int,
    @SerializedName("ot_sotrudnika_id") val otSotrudnikaId: Int,
    @SerializedName("komu_id") val komuId: Int?,
    @SerializedName("zayavka_id") val zayavkaId: Int?,
    val soobshenie: String,
    val prochitano: Boolean?,
    @SerializedName("data_sozdaniya") val dataSozdaniya: String?,
    @SerializedName("ot_fio") val otFio: String?,
    @SerializedName("komu_fio") val komuFio: String?
)

data class RefItem(val id: Int, val nazvanie: String)
data class RefModel(val id: Int, val nazvanie: String, @SerializedName("marka_id") val markaId: Int?)

data class RefsResponse(
    val statuses: List<RefItem>,
    val roles: List<RefItem>,
    @SerializedName("tipy_remonta") val tipyRemonta: List<RefItem>,
    val marki: List<RefItem>,
    val modeli: List<RefModel>,
    val podrazdeleniya: List<RefItem>
)

data class ZayavkiResponse(val zayavki: List<Zayavka>)
data class TsResponse(val ts: List<TS>)
data class TsOneResponse(val ts: TS)
data class SotrudnikiResponse(val sotrudniki: List<Sotrudnik>)
data class MekhanikiActiveResponse(val mekhaniki: List<MekhanikActive>)
data class FeedbackResponse(val messages: List<FeedbackMsg>)

data class Conversation(
    val id: Int,
    val fio: String,
    val login: String?,
    @SerializedName("rol_id") val rolId: Int?,
    @SerializedName("rol_name") val rolName: String?,
    @SerializedName("last_message") val lastMessage: String?,
    @SerializedName("last_time") val lastTime: String?,
    val unread: Int?
)

data class ConversationsResponse(val conversations: List<Conversation>)
data class UnreadResponse(val count: Int)
data class MeResponse(val user: Sotrudnik)
data class SimpleResponse(val success: Boolean? = null, val error: String? = null, val id: Int? = null)

// Requests
data class CreateZayavkaRequest(
    @SerializedName("ts_id") val tsId: Int,
    @SerializedName("tip_remonta_id") val tipRemontaId: Int,
    val opisanie: String,
    val prioritet: Int? = 3
)

data class UpdateZayavkaRequest(
    @SerializedName("ts_id") val tsId: Int? = null,
    @SerializedName("tip_remonta_id") val tipRemontaId: Int? = null,
    val opisanie: String? = null,
    val prioritet: Int? = null,
    @SerializedName("status_id") val statusId: Int? = null
)

data class StatusChangeRequest(
    @SerializedName("status_id") val statusId: Int,
    val kommentariy: String? = null,
    val itog: String? = null,
    @SerializedName("stoimost_rabot") val stoimostRabot: Double? = null,
    @SerializedName("stoimost_zapchastey") val stoimostZapchastey: Double? = null
)

data class UpdateTsRequest(
    @SerializedName("tekuschee_sostoyanie") val tekuscheeSostoyanie: String? = null,
    val probeg: Int? = null
)

data class CreateSotrudnikRequest(
    val fio: String,
    val login: String,
    val password: String,
    @SerializedName("rol_id") val rolId: Int,
    @SerializedName("podrazdelenie_id") val podrazdelenieId: Int? = 1
)

data class UpdateSotrudnikRequest(
    val fio: String? = null,
    val login: String? = null,
    val password: String? = null,
    @SerializedName("rol_id") val rolId: Int? = null,
    @SerializedName("podrazdelenie_id") val podrazdelenieId: Int? = null
)

data class FeedbackRequest(
    val soobshenie: String,
    @SerializedName("komu_id") val komuId: Int? = null,
    @SerializedName("zayavka_id") val zayavkaId: Int? = null
)
