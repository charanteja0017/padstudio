package com.charan.padstudio.core.home

/**
 * Hand-picked groups for apps common on Indian and global tablets, checked before flowerpot (whose
 * 2019 lists miss UPI apps, OTT services and edtech). Ids are the public store/Xiaomi package
 * names. Entries only ever match installed packages, so an id that is wrong or missing just has
 * no effect; the device probe (docs/device/raw) is used to correct this list over time.
 */
object CuratedApps {
    private fun group(group: AppGroup, vararg packages: String) = packages.associateWith { group }

    val GROUP_OF: Map<String, AppGroup> =
        group(
            AppGroup.STUDY,
            "com.google.android.apps.classroom", "com.duolingo", "com.byjus.thelearningapp", "com.unacademyapp",
            "xyz.penpencil.physicswala", "org.khanacademy.android", "org.coursera.android", "com.udemy.android",
            "com.microblink.photomath", "com.orion.notein.global", "com.flexcil.flexcilnote",
            "com.microsoft.office.onenote", "com.google.android.keep", "com.miui.notes", "ai.note.notepad.memo.journal.freenote",
            "com.steadfastinnovation.android.projectpapyrus",
        ) + group(
            AppGroup.WORK,
            "com.google.android.gm", "com.microsoft.office.outlook", "com.microsoft.teams", "us.zoom.videomeetings",
            "com.google.android.apps.tachyon", "com.google.android.apps.docs.editors.docs",
            "com.google.android.apps.docs.editors.sheets", "com.google.android.apps.docs.editors.slides",
            "com.google.android.apps.docs", "com.microsoft.office.officehubrow", "cn.wps.moffice_eng", "notion.id",
            "com.Slack", "com.linkedin.android", "com.google.android.calendar",
        ) + group(
            AppGroup.READING,
            "com.amazon.kindle", "com.google.android.apps.books", "com.adobe.reader", "com.xodo.pdf.reader",
            "com.flyersoft.moonreader", "org.readera", "com.medium.reader", "com.google.android.apps.magazines",
            "com.nis.app", "com.eterno",
        ) + group(
            AppGroup.CREATE,
            "com.canva.editor", "com.lemon.lvoverseas", "com.niksoftware.snapseed", "com.adobe.lrmobile",
            "jp.ne.ibis.ibispaintx.app", "com.brakefield.painter", "com.adsk.sketchbook", "com.tophatch.concepts", "com.miui.creation",
            "com.android.camera", "com.miui.gallery", "com.google.android.apps.photos",
        ) + group(
            AppGroup.SOCIAL,
            "com.whatsapp", "com.whatsapp.w4b", "com.instagram.android", "org.telegram.messenger",
            "com.twitter.android", "com.reddit.frontpage", "com.snapchat.android", "com.facebook.katana",
            "com.facebook.orca", "com.discord", "com.google.android.apps.messaging", "com.truecaller",
            "in.mohalla.sharechat", "com.instagram.barcelona",
        ) + group(
            AppGroup.WATCH_LISTEN,
            "com.google.android.youtube", "com.google.android.apps.youtube.music", "in.startv.hotstar",
            "com.netflix.mediaclient", "com.amazon.avod.thirdpartyclient", "com.spotify.music", "com.jio.media.jiobeats",
            "com.gaana", "com.miui.player", "com.miui.videoplayer", "com.sonyliv", "com.graymatrix.did",
            "com.mxtech.videoplayer.ad",
        ) + group(
            AppGroup.SHOP_PAY,
            "com.phonepe.app", "net.one97.paytm", "com.google.android.apps.nbu.paisa.user", "in.org.npci.upiapp",
            "com.flipkart.android", "in.amazon.mShop.android.shopping", "com.myntra.android", "in.swiggy.android",
            "com.application.zomato", "com.grofers.customerapp", "com.zeptoconsumerapp", "com.meesho.supply",
            "com.sbi.lotusintouch", "com.csam.icici.bank.imobile", "com.snapwork.hdfc", "com.axis.mobile",
            "com.dreamplug.androidapp",
        ) + group(
            AppGroup.LIFE,
            "com.google.android.apps.maps", "com.ubercab", "com.olacabs.customer", "in.redbus.android", "com.makemytrip",
            "cris.org.in.prs.ima", "com.google.android.apps.fitness", "com.mi.health", "com.miui.weather2",
            "com.practo.fabric",
        ) + group(
            AppGroup.TOOLS,
            "com.android.chrome", "com.mi.globalbrowser", "com.android.vending", "com.xiaomi.mipicks",
            "com.google.android.apps.nbu.files", "com.mi.android.globalFileexplorer", "com.miui.calculator",
            "com.android.deskclock", "com.miui.compass", "com.android.soundrecorder", "com.miui.screenrecorder",
            "com.google.android.apps.translate", "com.google.ar.lens", "com.google.android.googlequicksearchbox",
            "com.google.android.apps.authenticator2", "com.duokan.phone.remotecontroller", "com.miui.cleaner",
            "com.charan.padstudio", "io.github.sds100.keymapper",
        ) + group(
            AppGroup.SYSTEM,
            "com.android.settings", "com.miui.securitycenter", "com.android.thememanager", "com.xiaomi.account",
            "com.miui.miservice", "com.android.contacts", "com.android.mms", "com.miui.backup", "com.android.updater",
            "com.miui.bugreport",
        )

    /** Apps matched by label when their package id could not be verified in advance. */
    val GROUP_OF_LABEL: Map<String, AppGroup> = mapOf(
        "Notewise" to AppGroup.STUDY,
        "Mi Canvas" to AppGroup.CREATE,
    )
}
