package com.twinpane.app

data class CdnItem(
    val name: String,
    val description: String,
    val htmlCode: String,
)

object CdnLibraries {

    val items = listOf(
        CdnItem(
            name = "Bootstrap 5.3",
            description = "CSS & JS Framework for responsive UI",
            htmlCode = """<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>""",
        ),
        CdnItem(
            name = "Tailwind CSS (CDN)",
            description = "Utility-first CSS framework",
            htmlCode = """<script src="https://cdn.tailwindcss.com"></script>""",
        ),
        CdnItem(
            name = "Font Awesome 6",
            description = "Icon library & toolkit",
            htmlCode = """<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.1/css/all.min.css">""",
        ),
        CdnItem(
            name = "Animate.css",
            description = "Ready-to-use CSS animations",
            htmlCode = """<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/animate.css/4.1.1/animate.min.css">""",
        ),
        CdnItem(
            name = "Google Fonts (Poppins)",
            description = "Modern sans-serif typography",
            htmlCode = """<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Poppins:wght@300;400;600;700&display=swap" rel="stylesheet">""",
        ),
        CdnItem(
            name = "jQuery 3.7",
            description = "DOM manipulation library",
            htmlCode = """<script src="https://code.jquery.com/jquery-3.7.1.min.js"></script>""",
        ),
        CdnItem(
            name = "Vue.js 3",
            description = "Progressive JavaScript framework",
            htmlCode = """<script src="https://unpkg.com/vue@3/dist/vue.global.js"></script>""",
        ),
        CdnItem(
            name = "Chart.js",
            description = "Flexible JavaScript charting library",
            htmlCode = """<script src="https://cdn.jsdelivr.net/npm/chart.js"></script>""",
        ),
        CdnItem(
            name = "SweetAlert2",
            description = "Beautiful responsive alert dialogs",
            htmlCode = """<script src="https://cdn.jsdelivr.net/npm/sweetalert2@11"></script>""",
        ),
    )

    fun injectIntoHtml(currentHtml: String, cdnCode: String): Pair<String, Boolean> {
        if (currentHtml.contains(cdnCode.take(30))) {
            return currentHtml to false // Already injected
        }

        val updated = when {
            currentHtml.contains("</head>") -> {
                currentHtml.replace("</head>", "$cdnCode\n</head>")
            }
            currentHtml.contains("<body>") -> {
                currentHtml.replace("<body>", "$cdnCode\n<body>")
            }
            else -> {
                "$cdnCode\n$currentHtml"
            }
        }
        return updated to true
    }
}
