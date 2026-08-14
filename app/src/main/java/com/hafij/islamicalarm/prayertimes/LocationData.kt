package com.hafij.islamicalarm.prayertimes

import java.util.TimeZone

enum class LocationType {
    DIVISION,
    DISTRICT,
    THANA,
    GLOBAL_CITY,
    CUSTOM_GPS
}

enum class CalculationMethod(val titleBn: String, val fajrAngle: Double, val ishaAngle: Double, val ishaMinutesAfterMaghrib: Int = 0) {
    KARACHI_BD("ইসলামিক ফাউন্ডেশন বাংলাদেশ / করাচি", 18.0, 18.0),
    UMM_AL_QURA("উম্মুল কুরা (মক্কা মুকাররমা)", 18.5, 0.0, ishaMinutesAfterMaghrib = 90),
    MUSLIM_WORLD_LEAGUE("মুসলিম ওয়ার্ল্ড লীগ (MWL)", 18.0, 17.0),
    EGYPTIAN("মিশরীয় জেনারেল অথরিটি", 19.5, 17.5),
    ISNA("উত্তর আমেরিকা (ISNA)", 15.0, 15.0),
    GULF("উপসাগরীয় অঞ্চল (গাল্ফ)", 19.5, 0.0, ishaMinutesAfterMaghrib = 90)
}

data class District(
    val nameEn: String,
    val nameBn: String,
    val divisionBn: String,
    val lat: Double,
    val lng: Double,
    val offsetMinutes: Int = 0,
    val timeZone: Double = 6.0,
    val type: LocationType = LocationType.DISTRICT,
    val parentBn: String = divisionBn,
    val method: CalculationMethod = CalculationMethod.KARACHI_BD
) {
    val displayName: String
        get() = when (type) {
            LocationType.THANA -> "$nameBn ($parentBn)"
            LocationType.GLOBAL_CITY -> "$nameBn, $parentBn"
            LocationType.DISTRICT -> "$nameBn জেলা"
            LocationType.DIVISION -> "$nameBn বিভাগ"
            LocationType.CUSTOM_GPS -> "📍 $nameBn"
        }
}

object LocationData {

    // 8 Divisions of Bangladesh
    val divisions = listOf(
        District("Dhaka Division", "ঢাকা বিভাগ", "ঢাকা", 23.8103, 90.4125, 0, 6.0, LocationType.DIVISION, "বাংলাদেশ"),
        District("Chattogram Division", "চট্টগ্রাম বিভাগ", "চট্টগ্রাম", 22.3569, 91.7832, -5, 6.0, LocationType.DIVISION, "বাংলাদেশ"),
        District("Rajshahi Division", "রাজশাহী বিভাগ", "রাজশাহী", 24.3636, 88.6241, 7, 6.0, LocationType.DIVISION, "বাংলাদেশ"),
        District("Khulna Division", "খুলনা বিভাগ", "খুলনা", 22.8456, 89.5403, 3, 6.0, LocationType.DIVISION, "বাংলাদেশ"),
        District("Barishal Division", "বরিশাল বিভাগ", "বরিশাল", 22.7010, 90.3535, 0, 6.0, LocationType.DIVISION, "বাংলাদেশ"),
        District("Sylhet Division", "সিলেট বিভাগ", "সিলেট", 24.8949, 91.8687, -6, 6.0, LocationType.DIVISION, "বাংলাদেশ"),
        District("Rangpur Division", "রংপুর বিভাগ", "রংপুর", 25.7439, 89.2752, 4, 6.0, LocationType.DIVISION, "বাংলাদেশ"),
        District("Mymensingh Division", "ময়মনসিংহ বিভাগ", "ময়মনসিংহ", 24.7471, 90.4203, 0, 6.0, LocationType.DIVISION, "বাংলাদেশ")
    )

    // 64 Districts of Bangladesh
    val districts = listOf(
        // Dhaka Division
        District("Dhaka", "ঢাকা", "ঢাকা", 23.8103, 90.4125, 0),
        District("Gazipur", "গাজীপুর", "ঢাকা", 24.0023, 90.4264, 0),
        District("Narayanganj", "নারায়ণগঞ্জ", "ঢাকা", 23.6238, 90.5000, 0),
        District("Narsingdi", "নরসিংদী", "ঢাকা", 23.9322, 90.7154, -1),
        District("Tangail", "টাঙ্গাইল", "ঢাকা", 24.2513, 89.9167, 2),
        District("Manikganj", "মানিকগঞ্জ", "ঢাকা", 23.8644, 90.0047, 1),
        District("Munshiganj", "মুন্সীগঞ্জ", "ঢাকা", 23.5422, 90.5305, 0),
        District("Faridpur", "ফরিদপুর", "ঢাকা", 23.6071, 89.8429, 2),
        District("Gopalganj", "গোপালগঞ্জ", "ঢাকা", 23.0051, 89.8266, 2),
        District("Madaripur", "মাদারীপুর", "ঢাকা", 23.1641, 90.1897, 1),
        District("Rajbari", "রাজবাড়ী", "ঢাকা", 23.7574, 89.6445, 3),
        District("Shariatpur", "শরীয়তপুর", "ঢাকা", 23.2423, 90.4348, 0),
        District("Kishoreganj", "কিশোরগঞ্জ", "ঢাকা", 24.4449, 90.7766, -1),

        // Chittagong Division
        District("Chattogram", "চট্টগ্রাম", "চট্টগ্রাম", 22.3569, 91.7832, -5),
        District("Cox's Bazar", "কক্সবাজার", "চট্টগ্রাম", 21.4272, 92.0058, -6),
        District("Cumilla", "কুমিল্লা", "চট্টগ্রাম", 23.4607, 91.1809, -3),
        District("Feni", "ফেনী", "চট্টগ্রাম", 23.0186, 91.3966, -4),
        District("Brahmanbaria", "ব্রাহ্মণবাড়িয়া", "চট্টগ্রাম", 23.9571, 91.1119, -3),
        District("Chandpur", "চাঁদপুর", "চট্টগ্রাম", 23.2333, 90.6712, -1),
        District("Lakshmipur", "লক্ষ্মীপুর", "চট্টগ্রাম", 22.9425, 90.8412, -2),
        District("Noakhali", "নোয়াখালী", "চট্টগ্রাম", 22.8696, 91.0998, -3),
        District("Khagrachhari", "খাগড়াছড়ি", "চট্টগ্রাম", 23.1193, 91.9847, -6),
        District("Rangamati", "রাঙ্গামাটি", "চট্টগ্রাম", 22.6574, 92.1733, -7),
        District("Bandarban", "বান্দরবান", "চট্টগ্রাম", 22.1953, 92.2184, -7),

        // Rajshahi Division
        District("Rajshahi", "রাজশাহী", "রাজশাহী", 24.3636, 88.6241, 7),
        District("Bogura", "বগুড়া", "রাজশাহী", 24.8465, 89.3778, 4),
        District("Pabna", "পাবনা", "রাজশাহী", 24.0064, 89.2372, 5),
        District("Sirajganj", "সিরাজগঞ্জ", "রাজশাহী", 24.4534, 89.7008, 3),
        District("Naogaon", "নওগাঁ", "রাজশাহী", 24.7936, 88.9318, 6),
        District("Natore", "নাটোর", "রাজশাহী", 24.4206, 89.0003, 6),
        District("Chapai Nawabganj", "চাঁপাইনবাবগঞ্জ", "রাজশাহী", 24.5965, 88.2775, 8),
        District("Joypurhat", "জয়পুরহাট", "রাজশাহী", 25.0968, 89.0227, 5),

        // Khulna Division
        District("Khulna", "খুলনা", "খুলনা", 22.8456, 89.5403, 3),
        District("Jashore", "যশোর", "খুলনা", 23.1664, 89.2182, 5),
        District("Satkhira", "সাতক্ষীরা", "খুলনা", 22.7185, 89.0705, 5),
        District("Kushtia", "কুষ্টিয়া", "খুলনা", 23.9013, 89.1205, 5),
        District("Jhenaidah", "ঝিনাইদহ", "খুলনা", 23.5450, 89.1726, 5),
        District("Chuadanga", "চুয়াডাঙ্গা", "খুলনা", 23.6402, 88.8418, 6),
        District("Meherpur", "মেহেরপুর", "খুলনা", 23.7622, 88.6318, 7),
        District("Bagerhat", "বাগেরহাট", "খুলনা", 22.6516, 89.7859, 2),
        District("Narail", "নড়াইল", "খুলনা", 23.1725, 89.5127, 3),
        District("Magura", "মাগুরা", "খুলনা", 23.4873, 89.4198, 4),

        // Sylhet Division
        District("Sylhet", "সিলেট", "সিলেট", 24.8949, 91.8687, -6),
        District("Moulvibazar", "মৌলভীবাজার", "সিলেট", 24.4829, 91.7774, -5),
        District("Habiganj", "হবিগঞ্জ", "সিলেট", 24.3750, 91.4155, -4),
        District("Sunamganj", "সুনামগঞ্জ", "সিলেট", 25.0658, 91.3950, -4),

        // Barishal Division
        District("Barishal", "বরিশাল", "বরিশাল", 22.7010, 90.3535, 0),
        District("Patuakhali", "পটুয়াখালী", "বরিশাল", 22.3596, 90.3299, 0),
        District("Bhola", "ভোলা", "বরিশাল", 22.6859, 90.6482, -1),
        District("Pirojpur", "পিরোজপুর", "বরিশাল", 22.5841, 89.9720, 2),
        District("Barguna", "বরগুনা", "বরিশাল", 22.0953, 90.0770, 1),
        District("Jhalokati", "ঝালকাঠি", "বরিশাল", 22.6406, 90.1987, 1),

        // Rangpur Division
        District("Rangpur", "রংপুর", "রংপুর", 25.7439, 89.2752, 4),
        District("Dinajpur", "দিনাজপুর", "রংপুর", 25.6217, 88.6355, 7),
        District("Gaibandha", "গাইবান্ধা", "রংপুর", 25.3288, 89.5407, 3),
        District("Kurigram", "কুড়িগ্রাম", "রংপুর", 25.8054, 89.6362, 3),
        District("Lalmonirhat", "লালমনিরহাট", "রংপুর", 25.9923, 89.2847, 4),
        District("Nilphamari", "নীলফামারী", "রংপুর", 25.9318, 88.8560, 6),
        District("Panchagarh", "পঞ্চগড়", "রংপুর", 26.3411, 88.5542, 7),
        District("Thakurgaon", "ঠাকুরগাঁও", "রংপুর", 26.0337, 88.4617, 8),

        // Mymensingh Division
        District("Mymensingh", "ময়মনসিংহ", "ময়মনসিংহ", 24.7471, 90.4203, 0),
        District("Jamalpur", "জামালপুর", "ময়মনসিংহ", 24.9375, 89.9378, 2),
        District("Netrokona", "নেত্রকোণা", "ময়মনসিংহ", 24.8709, 90.7279, -1),
        District("Sherpur", "শেরপুর", "ময়মনসিংহ", 25.0205, 90.0153, 2)
    )

    // Thanas & Upazilas across Bangladesh
    val thanas = listOf(
        // Dhaka City & District Thanas
        District("Mirpur", "মিরপুর", "ঢাকা", 23.8223, 90.3654, 0, 6.0, LocationType.THANA, "ঢাকা"),
        District("Uttara", "উত্তরা", "ঢাকা", 23.8759, 90.3795, 0, 6.0, LocationType.THANA, "ঢাকা"),
        District("Dhanmondi", "ধানমন্ডি", "ঢাকা", 23.7465, 90.3760, 0, 6.0, LocationType.THANA, "ঢাকা"),
        District("Gulshan", "গুলশান", "ঢাকা", 23.7925, 90.4078, 0, 6.0, LocationType.THANA, "ঢাকা"),
        District("Banani", "বনানী", "ঢাকা", 23.7937, 90.4043, 0, 6.0, LocationType.THANA, "ঢাকা"),
        District("Mohammadpur", "মোহাম্মদপুর", "ঢাকা", 23.7658, 90.3584, 0, 6.0, LocationType.THANA, "ঢাকা"),
        District("Motijheel", "মতিঝিল", "ঢাকা", 23.7330, 90.4172, 0, 6.0, LocationType.THANA, "ঢাকা"),
        District("Badda", "বাড্ডা", "ঢাকা", 23.7806, 90.4267, 0, 6.0, LocationType.THANA, "ঢাকা"),
        District("Jatrabari", "যাত্রাবাড়ী", "ঢাকা", 23.7099, 90.4358, 0, 6.0, LocationType.THANA, "ঢাকা"),
        District("Savar", "সাভার", "ঢাকা", 23.8477, 90.2577, 1, 6.0, LocationType.THANA, "ঢাকা"),
        District("Dhamrai", "ধামরাই", "ঢাকা", 23.9186, 90.2114, 1, 6.0, LocationType.THANA, "ঢাকা"),
        District("Keraniganj", "কেরানীগঞ্জ", "ঢাকা", 23.6875, 90.3125, 0, 6.0, LocationType.THANA, "ঢাকা"),
        District("Nawabganj", "নবাবগঞ্জ", "ঢাকা", 23.6667, 90.1667, 1, 6.0, LocationType.THANA, "ঢাকা"),

        // Gazipur Thanas
        District("Tongi", "টঙ্গী", "গাজীপুর", 23.8967, 90.4022, 0, 6.0, LocationType.THANA, "গাজীপুর"),
        District("Kaliakair", "কালিয়াকৈর", "গাজীপুর", 24.0667, 90.2167, 1, 6.0, LocationType.THANA, "গাজীপুর"),
        District("Sreepur", "শ্রীপুর", "গাজীপুর", 24.2000, 90.4667, 0, 6.0, LocationType.THANA, "গাজীপুর"),
        District("Kapasia", "কাপাসিয়া", "গাজীপুর", 24.1167, 90.5667, -1, 6.0, LocationType.THANA, "গাজীপুর"),
        District("Kaliganj", "কালীগঞ্জ", "গাজীপুর", 23.9167, 90.5667, -1, 6.0, LocationType.THANA, "গাজীপুর"),

        // Narayanganj Thanas
        District("Siddhirganj", "সিদ্ধিরগঞ্জ", "নারায়ণগঞ্জ", 23.6833, 90.5167, 0, 6.0, LocationType.THANA, "নারায়ণগঞ্জ"),
        District("Fatullah", "ফতুল্লা", "নারায়ণগঞ্জ", 23.6500, 90.4833, 0, 6.0, LocationType.THANA, "নারায়ণগঞ্জ"),
        District("Rupganj", "রূপগঞ্জ", "নারায়ণগঞ্জ", 23.8000, 90.5167, 0, 6.0, LocationType.THANA, "নারায়ণগঞ্জ"),
        District("Sonargaon", "সোনারগাঁও", "নারায়ণগঞ্জ", 23.6500, 90.6000, 0, 6.0, LocationType.THANA, "নারায়ণগঞ্জ"),
        District("Araihazar", "আড়াইহাজার", "নারায়ণগঞ্জ", 23.7833, 90.6500, -1, 6.0, LocationType.THANA, "নারায়ণগঞ্জ"),

        // Chattogram Thanas
        District("Panchlaish", "পাঁচলাইশ", "চট্টগ্রাম", 22.3667, 91.8333, -5, 6.0, LocationType.THANA, "চট্টগ্রাম"),
        District("Kotwali Ctg", "কোতোয়ালী", "চট্টগ্রাম", 22.3384, 91.8317, -5, 6.0, LocationType.THANA, "চট্টগ্রাম"),
        District("Halishahar", "হালিশহর", "চট্টগ্রাম", 22.3167, 91.7833, -5, 6.0, LocationType.THANA, "চট্টগ্রাম"),
        District("Sitakunda", "সীতাকুণ্ড", "চট্টগ্রাম", 22.6167, 91.6667, -5, 6.0, LocationType.THANA, "চট্টগ্রাম"),
        District("Hathazari", "হাটহাজারী", "চট্টগ্রাম", 22.5083, 91.8083, -5, 6.0, LocationType.THANA, "চট্টগ্রাম"),
        District("Patiya", "পটিয়া", "চট্টগ্রাম", 22.2947, 91.9772, -6, 6.0, LocationType.THANA, "চট্টগ্রাম"),
        District("Raozan", "রাউজান", "চট্টগ্রাম", 22.5333, 91.9167, -6, 6.0, LocationType.THANA, "চট্টগ্রাম"),
        District("Boalkhali", "বোয়ালখালী", "চট্টগ্রাম", 22.3833, 91.9167, -6, 6.0, LocationType.THANA, "চট্টগ্রাম"),
        District("Anwara", "আনোয়ারা", "চট্টগ্রাম", 22.2167, 91.9167, -6, 6.0, LocationType.THANA, "চট্টগ্রাম"),
        District("Mirsharai", "মীরসরাই", "চট্টগ্রাম", 22.7722, 91.5750, -4, 6.0, LocationType.THANA, "চট্টগ্রাম"),

        // Cox's Bazar Thanas
        District("Teknaf", "টেকনাফ", "কক্সবাজার", 20.8667, 92.3000, -7, 6.0, LocationType.THANA, "কক্সবাজার"),
        District("Chakaria", "চকোরিয়া", "কক্সবাজার", 21.7861, 92.0778, -6, 6.0, LocationType.THANA, "কক্সবাজার"),
        District("Ramu", "রামু", "কক্সবাজার", 21.4583, 92.1000, -6, 6.0, LocationType.THANA, "কক্সবাজার"),
        District("Ukhia", "উখিয়া", "কক্সবাজার", 21.2833, 92.1667, -6, 6.0, LocationType.THANA, "কক্সবাজার"),
        District("Maheshkhali", "মহেশখালী", "কক্সবাজার", 21.5500, 91.9500, -6, 6.0, LocationType.THANA, "কক্সবাজার"),

        // Cumilla Thanas
        District("Chandina", "চান্দিনা", "কুমিল্লা", 23.4833, 90.9833, -2, 6.0, LocationType.THANA, "কুমিল্লা"),
        District("Daudkandi", "দাউদকান্দি", "কুমিল্লা", 23.5333, 90.7167, -1, 6.0, LocationType.THANA, "কুমিল্লা"),
        District("Debidwar", "দেবিদ্বার", "কুমিল্লা", 23.6000, 90.9833, -2, 6.0, LocationType.THANA, "কুমিল্লা"),
        District("Laksam", "লাকসাম", "কুমিল্লা", 23.2500, 91.1333, -3, 6.0, LocationType.THANA, "কুমিল্লা"),
        District("Muradnagar", "মুরাদনগর", "কুমিল্লা", 23.6333, 90.9333, -2, 6.0, LocationType.THANA, "কুমিল্লা"),
        District("Barura", "বরুড়া", "কুমিল্লা", 23.3667, 91.0500, -2, 6.0, LocationType.THANA, "কুমিল্লা"),
        District("Burichang", "বুড়িচং", "কুমিল্লা", 23.5500, 91.1333, -3, 6.0, LocationType.THANA, "কুমিল্লা"),

        // Sylhet Thanas
        District("Golapganj", "গোলাপগঞ্জ", "সিলেট", 24.8667, 92.0167, -7, 6.0, LocationType.THANA, "সিলেট"),
        District("Beanibazar", "বিয়ানীবাজার", "সিলেট", 24.8333, 92.1667, -7, 6.0, LocationType.THANA, "সিলেট"),
        District("Biswanath", "বিশ্বনাথ", "সিলেট", 24.7833, 91.7333, -5, 6.0, LocationType.THANA, "সিলেট"),
        District("Balaganj", "বালাগঞ্জ", "সিলেট", 24.6667, 91.8333, -6, 6.0, LocationType.THANA, "সিলেট"),
        District("Osmani Nagar", "ওসমানীনগর", "সিলেট", 24.7167, 91.7500, -5, 6.0, LocationType.THANA, "সিলেট"),
        District("Kanaighat", "কানাইঘাট", "সিলেট", 25.0167, 92.2667, -8, 6.0, LocationType.THANA, "সিলেট"),

        // Bogura & Rajshahi Thanas
        District("Sherpur Bogura", "শেরপুর", "বগুড়া", 24.6667, 89.4167, 4, 6.0, LocationType.THANA, "বগুড়া"),
        District("Shibganj Bogura", "শিবগঞ্জ", "বগুড়া", 24.9833, 89.3333, 4, 6.0, LocationType.THANA, "বগুড়া"),
        District("Ishwardi", "ঈশ্বরদী", "পাবনা", 24.1500, 89.0667, 5, 6.0, LocationType.THANA, "পাবনা"),
        District("Shahjadpur", "শাহজাদপুর", "সিরাজগঞ্জ", 24.1667, 89.6000, 3, 6.0, LocationType.THANA, "সিরাজগঞ্জ"),
        District("Ullapara", "উল্লাপাড়া", "সিরাজগঞ্জ", 24.3167, 89.5667, 3, 6.0, LocationType.THANA, "সিরাজগঞ্জ"),
        District("Puthia", "পুঠিয়া", "রাজশাহী", 24.3667, 88.8333, 6, 6.0, LocationType.THANA, "রাজশাহী"),
        District("Bagha", "বাঘা", "রাজশাহী", 24.2000, 88.7667, 7, 6.0, LocationType.THANA, "রাজশাহী"),
        District("Godagari", "গোদাগাড়ী", "রাজশাহী", 24.4667, 88.3333, 8, 6.0, LocationType.THANA, "রাজশাহী"),

        // Khulna & Jashore Thanas
        District("Dumuria", "ডুমুরিয়া", "খুলনা", 22.8083, 89.4250, 3, 6.0, LocationType.THANA, "খুলনা"),
        District("Rupsha", "রূপসা", "খুলনা", 22.8333, 89.5833, 3, 6.0, LocationType.THANA, "খুলনা"),
        District("Jhikargachha", "ঝিকরগাছা", "যশোর", 23.1000, 89.1333, 5, 6.0, LocationType.THANA, "যশোর"),
        District("Keshabpur", "কেশবপুর", "যশোর", 22.9000, 89.2167, 5, 6.0, LocationType.THANA, "যশোর"),
        District("Benapole", "বেনাপোল", "যশোর", 23.0333, 88.8833, 6, 6.0, LocationType.THANA, "যশোর"),
        District("Bheramara", "ভেড়ামারা", "কুষ্টিয়া", 24.0167, 88.9833, 6, 6.0, LocationType.THANA, "কুষ্টিয়া"),

        // Barishal & Rangpur & Mymensingh Thanas
        District("Bakerganj", "বাকেরগঞ্জ", "বরিশাল", 22.5500, 90.3333, 0, 6.0, LocationType.THANA, "বরিশাল"),
        District("Babuganj", "বাবুগঞ্জ", "বরিশাল", 22.8167, 90.3167, 0, 6.0, LocationType.THANA, "বরিশাল"),
        District("Pirganj", "পীরগঞ্জ", "রংপুর", 25.4167, 89.3167, 4, 6.0, LocationType.THANA, "রংপুর"),
        District("Mithapukur", "মিঠাপুকুর", "রংপুর", 25.5667, 89.2833, 4, 6.0, LocationType.THANA, "রংপুর"),
        District("Birganj", "বীরগঞ্জ", "দিনাজপুর", 25.8667, 88.6667, 7, 6.0, LocationType.THANA, "দিনাজপুর"),
        District("Saidpur", "সৈয়দপুর", "নীলফামারী", 25.7833, 88.9000, 6, 6.0, LocationType.THANA, "নীলফামারী"),
        District("Muktagachha", "মুক্তাগাছা", "ময়মনসিংহ", 24.7667, 90.2667, 1, 6.0, LocationType.THANA, "ময়মনসিংহ"),
        District("Fulbaria", "ফুলবাড়িয়া", "ময়মনসিংহ", 24.6333, 90.2667, 1, 6.0, LocationType.THANA, "ময়মনসিংহ"),
        District("Trishal", "ত্রিশাল", "ময়মনসিংহ", 24.5833, 90.4000, 0, 6.0, LocationType.THANA, "ময়মনসিংহ"),
        District("Bhaluka", "ভালুকা", "ময়মনসিংহ", 24.2333, 90.3833, 0, 6.0, LocationType.THANA, "ময়মনসিংহ")
    )

    // Major Islamic & Worldwide Cities
    val globalCities = listOf(
        // Saudi Arabia
        District("Makkah", "মক্কা মুকাররমা", "সৌদি আরব", 21.4225, 39.8262, 0, 3.0, LocationType.GLOBAL_CITY, "সৌদি আরব", CalculationMethod.UMM_AL_QURA),
        District("Madinah", "মদিনা মুনাওয়ারা", "সৌদি আরব", 24.4672, 39.6111, 0, 3.0, LocationType.GLOBAL_CITY, "সৌদি আরব", CalculationMethod.UMM_AL_QURA),
        District("Riyadh", "রিয়াদ", "সৌদি আরব", 24.7136, 46.6753, 0, 3.0, LocationType.GLOBAL_CITY, "সৌদি আরব", CalculationMethod.UMM_AL_QURA),
        District("Jeddah", "জেদ্দা", "সৌদি আরব", 21.5433, 39.1728, 0, 3.0, LocationType.GLOBAL_CITY, "সৌদি আরব", CalculationMethod.UMM_AL_QURA),

        // UAE & Gulf
        District("Dubai", "দুবাই", "সংযুক্ত আরব আমিরাত", 25.2048, 55.2708, 0, 4.0, LocationType.GLOBAL_CITY, "সংযুক্ত আরব আমিরাত", CalculationMethod.GULF),
        District("Abu Dhabi", "আবুধাবি", "সংযুক্ত আরব আমিরাত", 24.4539, 54.3773, 0, 4.0, LocationType.GLOBAL_CITY, "সংযুক্ত আরব আমিরাত", CalculationMethod.GULF),
        District("Sharjah", "শারজাহ", "সংযুক্ত আরব আমিরাত", 25.3463, 55.4209, 0, 4.0, LocationType.GLOBAL_CITY, "সংযুক্ত আরব আমিরাত", CalculationMethod.GULF),
        District("Doha", "দোহা", "কাতার", 25.2854, 51.5310, 0, 3.0, LocationType.GLOBAL_CITY, "কাতার", CalculationMethod.GULF),
        District("Kuwait City", "কুয়েত সিটি", "কুয়েত", 29.3759, 47.9774, 0, 3.0, LocationType.GLOBAL_CITY, "কুয়েত", CalculationMethod.GULF),
        District("Muscat", "মাস্কাট", "ওমান", 23.5880, 58.3829, 0, 4.0, LocationType.GLOBAL_CITY, "ওমান", CalculationMethod.GULF),
        District("Manama", "মানামা", "বাহরাইন", 26.2285, 50.5860, 0, 3.0, LocationType.GLOBAL_CITY, "বাহরাইন", CalculationMethod.GULF),

        // Middle East & Africa
        District("Cairo", "কায়রো", "মিশর", 30.0444, 31.2357, 0, 2.0, LocationType.GLOBAL_CITY, "মিশর", CalculationMethod.EGYPTIAN),
        District("Alexandria", "আলেকজান্দ্রিয়া", "মিশর", 31.2001, 29.9187, 0, 2.0, LocationType.GLOBAL_CITY, "মিশর", CalculationMethod.EGYPTIAN),
        District("Istanbul", "ইস্তাম্বুল", "তুরস্ক", 41.0082, 28.9784, 0, 3.0, LocationType.GLOBAL_CITY, "তুরস্ক", CalculationMethod.MUSLIM_WORLD_LEAGUE),
        District("Ankara", "আঙ্কারা", "তুরস্ক", 39.9334, 32.8597, 0, 3.0, LocationType.GLOBAL_CITY, "তুরস্ক", CalculationMethod.MUSLIM_WORLD_LEAGUE),
        District("Jerusalem", "জেরুজালেম (বায়তুল মুকাদ্দাস)", "ফিলিস্তিন", 31.7683, 35.2137, 0, 2.0, LocationType.GLOBAL_CITY, "ফিলিস্তিন", CalculationMethod.MUSLIM_WORLD_LEAGUE),
        District("Amman", "আম্মান", "জর্ডান", 31.9454, 35.9284, 0, 3.0, LocationType.GLOBAL_CITY, "জর্ডান", CalculationMethod.MUSLIM_WORLD_LEAGUE),

        // South & Southeast Asia
        District("Kolkata", "কলকাতা", "ভারত", 22.5726, 88.3639, 0, 5.5, LocationType.GLOBAL_CITY, "ভারত", CalculationMethod.KARACHI_BD),
        District("Delhi", "দিল্লি", "ভারত", 28.7041, 77.1025, 0, 5.5, LocationType.GLOBAL_CITY, "ভারত", CalculationMethod.KARACHI_BD),
        District("Mumbai", "মুম্বাই", "ভারত", 19.0760, 72.8777, 0, 5.5, LocationType.GLOBAL_CITY, "ভারত", CalculationMethod.KARACHI_BD),
        District("Hyderabad", "হায়দ্রাবাদ", "ভারত", 17.3850, 78.4867, 0, 5.5, LocationType.GLOBAL_CITY, "ভারত", CalculationMethod.KARACHI_BD),
        District("Chennai", "চেন্নাই", "ভারত", 13.0827, 80.2707, 0, 5.5, LocationType.GLOBAL_CITY, "ভারত", CalculationMethod.KARACHI_BD),
        District("Karachi", "করাচি", "পাকিস্তান", 24.8607, 67.0011, 0, 5.0, LocationType.GLOBAL_CITY, "পাকিস্তান", CalculationMethod.KARACHI_BD),
        District("Lahore", "লাহোর", "পাকিস্তান", 31.5204, 74.3587, 0, 5.0, LocationType.GLOBAL_CITY, "পাকিস্তান", CalculationMethod.KARACHI_BD),
        District("Islamabad", "ইসলামাবাদ", "পাকিস্তান", 33.6844, 73.0479, 0, 5.0, LocationType.GLOBAL_CITY, "পাকিস্তান", CalculationMethod.KARACHI_BD),
        District("Kuala Lumpur", "কুয়ালালামপুর", "মালয়েশিয়া", 3.1390, 101.6869, 0, 8.0, LocationType.GLOBAL_CITY, "মালয়েশিয়া", CalculationMethod.MUSLIM_WORLD_LEAGUE),
        District("Jakarta", "জাকার্তা", "ইন্দোনেশিয়া", -6.2088, 106.8456, 0, 7.0, LocationType.GLOBAL_CITY, "ইন্দোনেশিয়া", CalculationMethod.MUSLIM_WORLD_LEAGUE),
        District("Singapore", "সিঙ্গাপুর", "সিঙ্গাপুর", 1.3521, 103.8198, 0, 8.0, LocationType.GLOBAL_CITY, "সিঙ্গাপুর", CalculationMethod.MUSLIM_WORLD_LEAGUE),

        // Europe, Americas & Oceania
        District("London", "লন্ডন", "যুক্তরাজ্য", 51.5074, -0.1278, 0, 0.0, LocationType.GLOBAL_CITY, "যুক্তরাজ্য", CalculationMethod.MUSLIM_WORLD_LEAGUE),
        District("Birmingham", "বার্মিংহাম", "যুক্তরাজ্য", 52.4862, -1.8904, 0, 0.0, LocationType.GLOBAL_CITY, "যুক্তরাজ্য", CalculationMethod.MUSLIM_WORLD_LEAGUE),
        District("Manchester", "ম্যানচেস্টার", "যুক্তরাজ্য", 53.4808, -2.2426, 0, 0.0, LocationType.GLOBAL_CITY, "যুক্তরাজ্য", CalculationMethod.MUSLIM_WORLD_LEAGUE),
        District("New York", "নিউইয়র্ক", "যুক্তরাষ্ট্র", 40.7128, -74.0060, 0, -5.0, LocationType.GLOBAL_CITY, "যুক্তরাষ্ট্র", CalculationMethod.ISNA),
        District("Toronto", "টরন্টো", "কানাডা", 43.6532, -79.3832, 0, -5.0, LocationType.GLOBAL_CITY, "কানাডা", CalculationMethod.ISNA),
        District("Sydney", "সিডনি", "অস্ট্রেলিয়া", -33.8688, 151.2093, 0, 10.0, LocationType.GLOBAL_CITY, "অস্ট্রেলিয়া", CalculationMethod.MUSLIM_WORLD_LEAGUE),
        District("Tokyo", "টোকিও", "জাপান", 35.6762, 139.6503, 0, 9.0, LocationType.GLOBAL_CITY, "জাপান", CalculationMethod.MUSLIM_WORLD_LEAGUE),
        District("Seoul", "সিউল", "দক্ষিণ কোরিয়া", 37.5665, 126.9780, 0, 9.0, LocationType.GLOBAL_CITY, "দক্ষিণ কোরিয়া", CalculationMethod.MUSLIM_WORLD_LEAGUE),
        District("Paris", "প্যারিস", "ফ্রান্স", 48.8566, 2.3522, 0, 1.0, LocationType.GLOBAL_CITY, "ফ্রান্স", CalculationMethod.MUSLIM_WORLD_LEAGUE),
        District("Berlin", "বার্লিন", "জার্মানি", 52.5200, 13.4050, 0, 1.0, LocationType.GLOBAL_CITY, "জার্মানি", CalculationMethod.MUSLIM_WORLD_LEAGUE),
        District("Rome", "রোম", "ইতালি", 41.9028, 12.4964, 0, 1.0, LocationType.GLOBAL_CITY, "ইতালি", CalculationMethod.MUSLIM_WORLD_LEAGUE)
    )

    val allLocations: List<District> by lazy {
        districts + divisions + thanas + globalCities
    }

    fun getDefaultDistrict(): District = districts[0] // Dhaka

    fun findDistrict(nameBn: String): District {
        return allLocations.find { it.nameBn.equals(nameBn, ignoreCase = true) || it.nameEn.equals(nameBn, ignoreCase = true) }
            ?: getDefaultDistrict()
    }

    fun search(query: String): List<District> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return districts

        return allLocations.filter { item ->
            item.nameBn.lowercase().contains(q) ||
            item.nameEn.lowercase().contains(q) ||
            item.divisionBn.lowercase().contains(q) ||
            item.parentBn.lowercase().contains(q)
        }
    }

    fun findClosestLocation(lat: Double, lng: Double): District {
        var minDistance = Double.MAX_VALUE
        var closest = getDefaultDistrict()

        for (loc in allLocations) {
            val dist = distanceBetween(lat, lng, loc.lat, loc.lng)
            if (dist < minDistance) {
                minDistance = dist
                closest = loc
            }
        }
        return closest
    }

    private fun distanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return 6371 * c // km
    }
}
