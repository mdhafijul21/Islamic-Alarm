package com.hafij.islamicalarm.prayertimes

data class District(
    val nameEn: String,
    val nameBn: String,
    val divisionBn: String,
    val lat: Double,
    val lng: Double,
    val offsetMinutes: Int // offset relative to Dhaka (minutes to add/subtract)
)

object DistrictData {
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

    fun getDefaultDistrict(): District = districts[0] // Dhaka

    fun findDistrict(nameBn: String): District {
        return districts.find { it.nameBn == nameBn } ?: getDefaultDistrict()
    }
}
