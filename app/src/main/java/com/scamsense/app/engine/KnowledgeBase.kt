package com.scamsense.app.engine

/**
 * A group of related terms. A rule contributes its [weight] at most once per analysis,
 * no matter how many of its terms appear, so long messages are not over-penalised.
 */
data class KeywordRule(
    val id: String,
    val category: SignalCategory,
    val weight: Int,
    val title: String,
    val detail: String,
    val terms: List<String>,
)

/** A known scam message pattern used for similarity ("meaning") matching. */
data class ScamTemplate(val scamType: String, val text: String)

/**
 * Offline knowledge base. Everything ships inside the APK: the app never needs the
 * internet, so no message the user checks ever leaves the device.
 */
object KnowledgeBase {

    val rules: List<KeywordRule> = listOf(
        KeywordRule(
            id = "credentials",
            category = SignalCategory.SENSITIVE_INFO,
            weight = 30,
            title = "ขอรหัสลับ เช่น OTP หรือรหัสผ่าน",
            detail = "ธนาคารและหน่วยงานจริงจะไม่ขอรหัส OTP, PIN หรือรหัสผ่านจากคุณ ไม่ว่าทางโทรศัพท์ SMS หรือแชท",
            terms = listOf(
                "otp", "รหัสotp", "รหัสโอทีพี", "โอทีพี", "รหัสยืนยัน", "รหัส6หลัก", "รหัส 6 หลัก",
                "รหัสผ่าน", "password", "pin", "รหัสpin", "cvv", "cvc", "เลขหลังบัตร",
                "เลข3ตัวหลังบัตร", "รหัสหลังบัตร", "username", "ชื่อผู้ใช้และรหัส",
            ),
        ),
        KeywordRule(
            id = "identity",
            category = SignalCategory.SENSITIVE_INFO,
            weight = 22,
            title = "ขอข้อมูลส่วนตัวหรือเลขบัญชี",
            detail = "การขอเลขบัญชี เลขบัตรประชาชน หรือข้อมูลส่วนตัว อาจนำไปใช้เปิดบัญชีม้าหรือขโมยเงินได้",
            terms = listOf(
                "บัญชีธนาคาร", "เลขบัญชี", "บชธนาคาร", "รหัสบัตรประชาชน", "เลขบัตรประชาชน",
                "บัตรประชาชน", "บัตรปชช", "รหัสปชช", "เลขปชช", "เลขประจำตัวประชาชน",
                "ข้อมูลส่วนบุคคล", "ข้อมูลส่วนตัว", "วันเดือนปีเกิด", "เลขบัตรเครดิต",
                "ถ่ายรูปคู่บัตร", "ถ่ายรูปคู่กับบัตร", "สแกนใบหน้า", "ยืนยันใบหน้า", "ข้อมูลบัตร",
            ),
        ),
        KeywordRule(
            id = "urgency",
            category = SignalCategory.URGENCY,
            weight = 15,
            title = "เร่งรีบ หรือขู่ว่าจะระงับบัญชี",
            detail = "มิจฉาชีพมักเร่งให้รีบทำ เพื่อไม่ให้คุณมีเวลาคิดหรือถามคนอื่น",
            terms = listOf(
                "ด่วน", "ด่วนที่สุด", "โดยด่วน", "ทันที", "ภายใน24ชั่วโมง", "ภายในวันนี้",
                "ระงับ", "ถูกระงับ", "อายัด", "ถูกอายัด", "หมดอายุ", "ปิดบัญชี", "บัญชีจะถูกปิด",
                "ล็อกบัญชี", "บัญชีถูกล็อก", "มิฉะนั้น", "หากไม่ดำเนินการ", "ตัดไฟ", "ตัดน้ำ",
                "สิทธิ์มีจำนวนจำกัด", "วันนี้วันเดียว", "urgent", "suspended",
            ),
        ),
        KeywordRule(
            id = "authority",
            category = SignalCategory.IMPERSONATION,
            weight = 15,
            title = "อ้างเป็นตำรวจหรือหน่วยงานรัฐ",
            detail = "หน่วยงานรัฐจะไม่โทรหรือส่งข้อความมาให้โอนเงิน หรือขอข้อมูลบัญชีของคุณ",
            terms = listOf(
                "ตำรวจ", "สถานีตำรวจ", "ดีเอสไอ", "dsi", "ปปง", "ปปส", "สรรพากร", "กรมสรรพากร",
                "กรมที่ดิน", "การไฟฟ้า", "กฟภ", "กฟน", "การประปา", "หมายศาล", "ศาลอาญา", "ศาลแพ่ง", "อัยการ", "ศุลกากร",
                "ธนาคารแห่งประเทศไทย", "ธปท", "ประกันสังคม", "กสทช", "กรมการขนส่ง",
                "กรมการจัดหางาน", "เจ้าหน้าที่รัฐ", "กระทรวง",
            ),
        ),
        KeywordRule(
            id = "bank_brand",
            category = SignalCategory.IMPERSONATION,
            weight = 5,
            title = "กล่าวถึงธนาคาร",
            detail = "ข้อความที่อ้างชื่อธนาคารอาจเป็นของปลอมได้ ถ้าไม่แน่ใจให้โทรเบอร์ที่อยู่หลังบัตรเอง",
            terms = listOf(
                "ธนาคาร", "กสิกร", "kbank", "ไทยพาณิชย์", "scb", "กรุงไทย", "ktb", "กรุงศรี",
                "ออมสิน", "ธกส", "ทีทีบี", "ttb",
            ),
        ),
        KeywordRule(
            id = "delivery_brand",
            category = SignalCategory.IMPERSONATION,
            weight = 6,
            title = "อ้างเรื่องพัสดุหรือบริษัทขนส่ง",
            detail = "ข้อความเรื่องพัสดุค้างส่ง ที่ให้กดลิงก์หรือจ่ายเงิน เป็นกลโกงที่พบบ่อยมาก",
            terms = listOf(
                "พัสดุ", "ไปรษณีย์", "ขนส่ง", "kerry", "เคอรี่", "แฟลช", "flash", "thaipost",
                "ค้างส่ง", "จัดส่งไม่สำเร็จ",
            ),
        ),
        KeywordRule(
            id = "money_transfer",
            category = SignalCategory.MONEY,
            weight = 15,
            title = "ให้โอนเงินหรือจ่ายค่าธรรมเนียม",
            detail = "การให้จ่ายเงินก่อน ไม่ว่าจะเรียกว่าค่าธรรมเนียม ค่ามัดจำ หรือภาษี เป็นสัญญาณกลโกงที่ชัดเจน",
            terms = listOf(
                "โอนเงิน", "โอนมา", "โอนเข้าบัญชี", "โอนก่อน", "ค่าธรรมเนียม", "ค่ามัดจำ", "มัดจำ",
                "ค่าดำเนินการ", "ค่าประกัน", "สำรองเงิน", "ชำระภาษีก่อน", "จ่ายค่าภาษีก่อน",
            ),
        ),
        KeywordRule(
            id = "prize_refund",
            category = SignalCategory.MONEY,
            weight = 20,
            title = "อ้างว่าได้รางวัล เงินคืน หรือเงินช่วยเหลือ",
            detail = "ถ้าคุณไม่ได้สมัครหรือร่วมกิจกรรมใดไว้ ข้อความว่าได้รางวัลหรือได้เงินคืนมักเป็นกลโกง",
            terms = listOf(
                "ได้รับรางวัล", "ถูกรางวัล", "รางวัลใหญ่", "ของรางวัล", "เงินคืน", "คืนเงิน",
                "คืนภาษี", "ขอคืนภาษี", "เงินช่วยเหลือ", "เงินชดเชย", "เยียวยา", "เงินดิจิทัล",
                "ค่าประกันมิเตอร์", "จับฉลาก", "ผู้โชคดี",
            ),
        ),
        KeywordRule(
            id = "loan",
            category = SignalCategory.MONEY,
            weight = 15,
            title = "เสนอเงินกู้อนุมัติง่ายผิดปกติ",
            detail = "เงินกู้จริงไม่เรียกเก็บเงินก่อนอนุมัติ และไม่ติดต่อผ่านไลน์ส่วนตัว",
            terms = listOf(
                "เงินกู้", "สินเชื่อ", "กู้ด่วน", "อนุมัติไว", "อนุมัติทันที", "ไม่ต้องค้ำ",
                "ไม่เช็คบูโร", "ติดบูโรก็กู้ได้", "ดอกเบี้ยต่ำ", "เงินด่วน",
            ),
        ),
        KeywordRule(
            id = "investment",
            category = SignalCategory.MONEY,
            weight = 20,
            title = "ชวนลงทุน อ้างผลตอบแทนสูง",
            detail = "ไม่มีการลงทุนใดการันตีกำไรสูงได้จริง การชวนลงทุนผ่านแชทมักเป็นแชร์ลูกโซ่หรือแอปปลอม",
            terms = listOf(
                "ผลตอบแทน", "ปันผล", "กำไรต่อวัน", "รายได้ต่อวัน", "ลงทุนน้อย", "การันตีกำไร",
                "การันตีผลตอบแทน", "กำไรเป็นเท่าตัว", "เทรด", "คริปโต", "forex", "ฟอเร็กซ์",
                "หุ้นตัวนี้", "กลุ่มวีไอพี",
            ),
        ),
        KeywordRule(
            id = "job_scam",
            category = SignalCategory.MONEY,
            weight = 18,
            title = "ชวนทำงานออนไลน์ รายได้ดีเกินจริง",
            detail = "งานที่ให้กดไลค์หรือรับออเดอร์ แล้วให้สำรองเงินก่อน เป็นกลโกงหลอกให้โอนเงิน",
            terms = listOf(
                "งานออนไลน์", "ทำงานที่บ้าน", "งานพาร์ทไทม์", "กดไลค์", "กดแชร์", "รับออเดอร์",
                "รายได้วันละ", "ค่าคอมมิชชัน",
            ),
        ),
        KeywordRule(
            id = "threat_legal",
            category = SignalCategory.THREAT,
            weight = 25,
            title = "ขู่ว่าคุณพัวพันคดีหรือจะถูกดำเนินคดี",
            detail = "ตำรวจจริงจะส่งหมายเรียกเป็นหนังสือ ไม่โทรมาข่มขู่หรือให้โอนเงินเพื่อพิสูจน์ความบริสุทธิ์",
            terms = listOf(
                "พัวพัน", "คดี", "ฟอกเงิน", "ยาเสพติด", "พัสดุผิดกฎหมาย", "สิ่งผิดกฎหมาย",
                "หมายจับ", "ดำเนินคดี", "ถูกจับ", "ติดคุก", "ค้างชำระ", "ถูกฟ้อง", "บัญชีม้า",
                "ฟ้องร้อง",
            ),
        ),
        KeywordRule(
            id = "action_link",
            category = SignalCategory.ACTION,
            weight = 12,
            title = "ให้กดลิงก์ แอดไลน์ หรือกรอกข้อมูล",
            detail = "ลิงก์ในข้อความอาจพาไปเว็บปลอมที่หน้าตาเหมือนของจริง เพื่อขโมยข้อมูลของคุณ",
            terms = listOf(
                "คลิกลิงก์", "กดลิงก์", "ที่ลิงก์", "คลิก", "แอดไลน์", "addline", "เพิ่มเพื่อน",
                "ไอดีไลน์", "ทักไลน์", "lineid", "สแกนqr", "สแกนคิวอาร์", "ยืนยันตัวตน",
                "อัปเดตข้อมูล", "อัพเดทข้อมูล", "กรอกข้อมูล", "ลงทะเบียน", "กด9", "กด 9",
            ),
        ),
        KeywordRule(
            id = "install_app",
            category = SignalCategory.ACTION,
            weight = 25,
            title = "ให้ติดตั้งแอปจากลิงก์",
            detail = "แอปที่ส่งมาทางลิงก์หรือแชทอาจเป็นแอปดูดเงิน ที่ควบคุมโทรศัพท์และโอนเงินออกจากบัญชีได้",
            terms = listOf(
                "ติดตั้งแอป", "ดาวน์โหลดแอป", "โหลดแอป", "ติดตั้งโปรแกรม", "apk", "แอปของเจ้าหน้าที่",
                "แอปของเรา",
            ),
        ),
        KeywordRule(
            id = "remote_access",
            category = SignalCategory.REMOTE_ACCESS,
            weight = 40,
            title = "ขอควบคุมเครื่องหรือแชร์หน้าจอ",
            detail = "ถ้าให้สิทธิ์ควบคุมเครื่อง มิจฉาชีพจะเห็นทุกอย่างบนจอและโอนเงินออกได้ทันที",
            terms = listOf(
                "anydesk", "แอนนี่เดสก์", "teamviewer", "rustdesk", "quicksupport", "airdroid",
                "แชร์หน้าจอ", "การช่วยเหลือพิเศษ", "accessibility", "เปิดสิทธิ์การเข้าถึง",
                "ควบคุมเครื่อง",
            ),
        ),
        KeywordRule(
            id = "secrecy",
            category = SignalCategory.SECRECY,
            weight = 25,
            title = "บอกให้เก็บเป็นความลับ หรือห้ามวางสาย",
            detail = "การห้ามไม่ให้บอกครอบครัวหรือห้ามวางสาย เป็นวิธีตัดคุณจากคนที่จะช่วยเตือนได้",
            terms = listOf(
                "เป็นความลับ", "ห้ามวางสาย", "อย่าวางสาย", "ห้ามแจ้งใคร", "ห้ามปรึกษาใคร",
                "ห้ามบอกครอบครัว", "ห้ามบอกลูก", "ห้ามบอกญาติ", "อย่าบอกลูกหลาน", "อย่าเพิ่งบอก",
            ),
        ),
        KeywordRule(
            id = "safe_account",
            category = SignalCategory.MONEY,
            weight = 40,
            title = "ให้โอนเงินไป \"บัญชีปลอดภัย\" หรือเพื่อตรวจสอบ",
            detail = "ไม่มีหน่วยงานใดให้โอนเงินไปตรวจสอบ นี่คือกลโกงแก๊งคอลเซ็นเตอร์ที่พบบ่อยที่สุด",
            terms = listOf(
                "บัญชีปลอดภัย", "บัญชีตรวจสอบ", "ตรวจสอบเงิน", "ตรวจสอบเส้นทางการเงิน",
                "พิสูจน์ทรัพย์สิน", "โอนเพื่อตรวจสอบ", "แล้วจะคืนให้",
            ),
        ),
        KeywordRule(
            id = "gambling",
            category = SignalCategory.MONEY,
            weight = 15,
            title = "ชวนเล่นพนันออนไลน์",
            detail = "เว็บพนันออนไลน์ผิดกฎหมาย และมักโกงเงินหรือขโมยข้อมูลบัญชี",
            terms = listOf(
                "บาคาร่า", "สล็อต", "เว็บตรง", "เครดิตฟรี", "ฝากถอนไม่มีขั้นต่ำ", "แทงบอล",
            ),
        ),
    )

    /** Rule ids whose co-occurrence with [blockPartners] means "block" (do not act at all). */
    const val CREDENTIALS_RULE = "credentials"
    val blockPartners = setOf("action_link", "urgency", "authority", "money_transfer", "threat_legal", "install_app")
    val alwaysBlockRules = setOf("remote_access", "safe_account")

    val templates: List<ScamTemplate> = listOf(
        // Call-center gangs impersonating police / DSI
        ScamTemplate("แก๊งคอลเซ็นเตอร์อ้างเป็นตำรวจ", "สวัสดีค่ะ ติดต่อจากสถานีตำรวจ บัญชีธนาคารของท่านพัวพันกับคดีฟอกเงิน กรุณาโอนเงินมาตรวจสอบที่บัญชีปลอดภัย ห้ามบอกใคร"),
        ScamTemplate("แก๊งคอลเซ็นเตอร์อ้างเป็นตำรวจ", "เจ้าหน้าที่ดีเอสไอแจ้งว่าท่านมีหมายจับคดียาเสพติด ต้องยืนยันตัวตนผ่านวิดีโอคอลและห้ามวางสาย"),
        ScamTemplate("แก๊งคอลเซ็นเตอร์อ้างเป็นตำรวจ", "บัญชีของท่านถูกใช้เป็นบัญชีม้า ต้องโอนเงินทั้งหมดเพื่อตรวจสอบเส้นทางการเงิน แล้วจะคืนให้ภายหลัง"),
        ScamTemplate("พัสดุผิดกฎหมาย", "พัสดุของท่านมีสิ่งผิดกฎหมาย ถูกตีกลับที่ศุลกากร กด 9 เพื่อติดต่อเจ้าหน้าที่ มิฉะนั้นจะถูกดำเนินคดี"),
        // Bank / account suspension phishing
        ScamTemplate("หลอกว่าบัญชีถูกระงับ", "บัญชีธนาคารของคุณถูกระงับชั่วคราว กรุณายืนยันตัวตนภายใน 24 ชั่วโมงที่ลิงก์นี้ มิฉะนั้นบัญชีจะถูกปิด"),
        ScamTemplate("หลอกขอรหัส OTP", "แจ้งเตือนจากธนาคาร มีการเข้าสู่ระบบผิดปกติ กรุณากรอกรหัส OTP เพื่อยกเลิกรายการ"),
        ScamTemplate("หลอกขอข้อมูลบัตร", "บัตรเครดิตของท่านมียอดค้างชำระ กรุณาแจ้งเลขบัตรและเลขหลังบัตรเพื่อระงับการใช้งาน"),
        ScamTemplate("หลอกขอรหัส OTP", "ส่งรหัส 6 หลักที่เพิ่งได้รับมาให้หน่อย พอดีส่งผิดเบอร์"),
        ScamTemplate("หลอกขอรหัส OTP", "บัญชีไลน์ของคุณจะถูกระงับ กรุณายืนยันตัวตนด้วยรหัสที่ส่งไปทาง SMS"),
        // Parcel
        ScamTemplate("หลอกเรื่องพัสดุ", "พัสดุของคุณไม่สามารถจัดส่งได้เนื่องจากที่อยู่ไม่ครบถ้วน กรุณาอัปเดตข้อมูลและชำระค่าธรรมเนียมที่ลิงก์"),
        ScamTemplate("หลอกเรื่องพัสดุ", "ไปรษณีย์แจ้ง มีพัสดุค้างส่ง กรุณาคลิกลิงก์เพื่อนัดหมายการจัดส่งใหม่"),
        // Government / utility refunds
        ScamTemplate("แอบอ้างหน่วยงานรัฐให้ติดตั้งแอป", "การไฟฟ้าแจ้งคืนเงินค่าประกันมิเตอร์ กรุณาแอดไลน์เจ้าหน้าที่และติดตั้งแอปเพื่อรับเงินคืน"),
        ScamTemplate("แอบอ้างหน่วยงานรัฐ", "กรมสรรพากรแจ้งคืนภาษี ท่านมีสิทธิ์ได้รับเงินคืน กรุณากรอกข้อมูลบัญชีธนาคารที่ลิงก์"),
        ScamTemplate("แอบอ้างหน่วยงานรัฐ", "ประกันสังคมแจ้งสิทธิ์รับเงินชดเชย ลงทะเบียนยืนยันตัวตนผ่านลิงก์ภายในวันนี้"),
        ScamTemplate("แอบอ้างหน่วยงานรัฐ", "การไฟฟ้าแจ้งค่าไฟค้างชำระ จะตัดไฟภายในวันนี้ หากไม่ชำระทันที กรุณาติดต่อกลับ"),
        ScamTemplate("แอบอ้างหน่วยงานรัฐให้ติดตั้งแอป", "กรมที่ดินแจ้งให้ปรับปรุงข้อมูลที่ดิน ติดตั้งแอปเพื่อชำระภาษีที่ดินออนไลน์"),
        ScamTemplate("แอบอ้างหน่วยงานรัฐ", "รับเงินดิจิทัลจากรัฐ ลงทะเบียนผ่านลิงก์นี้ด่วน สิทธิ์มีจำนวนจำกัด"),
        // Prizes
        ScamTemplate("หลอกว่าได้รางวัล", "ยินดีด้วย คุณได้รับรางวัลโทรศัพท์มือถือ กรุณาโอนค่าธรรมเนียมจัดส่งเพื่อรับของรางวัล"),
        ScamTemplate("หลอกว่าได้รางวัล", "คุณถูกรางวัลเงินสด 100000 บาท จากการจับฉลากลูกค้า กรุณาแจ้งเลขบัญชีและชำระภาษีก่อนรับเงิน"),
        // Loans
        ScamTemplate("เงินกู้ปลอม", "สินเชื่อด่วน อนุมัติไว ไม่ต้องค้ำ ไม่เช็คบูโร ดอกเบี้ยต่ำ แอดไลน์สมัครได้เลย"),
        ScamTemplate("เงินกู้ปลอม", "ยินดีด้วย ท่านได้รับการอนุมัติเงินกู้ 50000 บาท กรุณาโอนค่ามัดจำและค่าดำเนินการก่อนรับเงิน"),
        // Investment
        ScamTemplate("หลอกลงทุน", "ลงทุนน้อย กำไรต่อวัน 10% การันตีผลตอบแทน ถอนได้ทันที สนใจทักไลน์"),
        ScamTemplate("หลอกลงทุน", "โอกาสลงทุนคริปโตกับผู้เชี่ยวชาญ ได้ผลตอบแทนสูง ปันผลทุกวัน ชวนเพื่อนได้โบนัส"),
        ScamTemplate("หลอกลงทุน", "หุ้นตัวนี้ขึ้นแน่ กลุ่มวีไอพีแนะนำ ลงทุนผ่านแอปของเรา ได้กำไรเป็นเท่าตัว"),
        // Jobs
        ScamTemplate("หลอกทำงานออนไลน์", "รับสมัครงานออนไลน์ ทำที่บ้าน รายได้วันละ 500-3000 บาท แค่กดไลค์กดแชร์ ทักไลน์"),
        ScamTemplate("หลอกทำงานออนไลน์", "งานพาร์ทไทม์รับออเดอร์ ต้องสำรองเงินก่อนแล้วได้คืนพร้อมค่าคอมมิชชัน"),
        // Relatives / romance
        ScamTemplate("แอบอ้างเป็นลูกหลานหรือคนรู้จัก", "แม่ครับ ผมเปลี่ยนเบอร์ใหม่ ตอนนี้มีเรื่องด่วน ช่วยโอนเงินให้ก่อนได้ไหม อย่าเพิ่งบอกพ่อ"),
        ScamTemplate("แอบอ้างเป็นลูกหลานหรือคนรู้จัก", "สวัสดีครับ จำผมได้ไหม เพื่อนเก่าเอง ตอนนี้เดือดร้อน ขอยืมเงินด่วน พรุ่งนี้คืน"),
        ScamTemplate("หลอกให้รักแล้วหลอกโอนเงิน", "ที่รัก ฉันส่งพัสดุของขวัญให้คุณ แต่ติดศุลกากร ต้องจ่ายค่าภาษีก่อนถึงจะได้รับ"),
        // Remote-control apps
        ScamTemplate("หลอกติดตั้งแอปควบคุมเครื่อง", "กรุณาดาวน์โหลดแอปของเจ้าหน้าที่และเปิดสิทธิ์การช่วยเหลือพิเศษ เพื่อดำเนินการคืนเงินให้ท่าน"),
        ScamTemplate("หลอกติดตั้งแอปควบคุมเครื่อง", "ให้ติดตั้งแอนนี่เดสก์ AnyDesk เพื่อให้เจ้าหน้าที่ช่วยตรวจสอบบัญชี"),
        // Gambling / shopping
        ScamTemplate("พนันออนไลน์", "เว็บตรง ไม่ผ่านเอเย่นต์ สล็อต บาคาร่า สมัครวันนี้รับเครดิตฟรี ฝากถอนไม่มีขั้นต่ำ"),
        ScamTemplate("ร้านค้าออนไลน์ปลอม", "สินค้าแบรนด์เนมลดราคา 90% วันนี้วันเดียว โอนก่อนส่งของ ไม่รับเก็บเงินปลายทาง"),
        ScamTemplate("ร้านค้าออนไลน์ปลอม", "ร้านค้าแจ้งว่าคุณสั่งของซ้ำ ต้องการคืนเงิน กรุณากดลิงก์และกรอกข้อมูลบัตร"),
        ScamTemplate("แอบอ้างบริษัทประกัน", "บริษัทประกันแจ้งว่าท่านได้รับเงินคืนจากกรมธรรม์ กรุณาโอนค่าดำเนินการก่อน"),
    )

    /** Phrases typical of genuine one-time-password SMS ("do not share this code"). */
    val otpNoticePhrases = listOf(
        "ห้ามเปิดเผย", "อย่าเปิดเผย", "ไม่ควรเปิดเผย", "ห้ามบอกรหัส", "อย่าบอกรหัส",
        "donotshare", "dontshare", "neversharethis",
    )

    // ---------------- Links ----------------

    val urlShorteners = setOf(
        "bit.ly", "tinyurl.com", "cutt.ly", "t.co", "shorturl.at", "rb.gy", "is.gd", "s.id",
        "t.ly", "ow.ly", "goo.gl", "tiny.cc", "rebrand.ly", "shorturl.asia", "urlis.net",
        "lnkd.in", "buff.ly", "bl.ink", "v.gd", "qr.co",
    )

    val suspiciousTlds = setOf(
        "xyz", "top", "icu", "click", "link", "vip", "live", "shop", "online", "site", "buzz",
        "cc", "tk", "ml", "ga", "cf", "gq", "monster", "rest", "cyou", "sbs", "cfd", "bond",
        "win", "bet", "loan", "work", "fun", "space", "website", "pw", "support", "help",
    )

    /** TLDs used only to recognise bare domains (without http://) inside free text. */
    val knownTlds: Set<String> = suspiciousTlds + setOf(
        "com", "net", "org", "info", "biz", "me", "ly", "io", "app", "co", "th", "asia", "cn",
        "ru", "in", "to", "gl", "gd", "at", "id", "us", "uk", "store", "tech", "pro", "dev",
        "page", "club", "today", "news", "mobi", "ws", "su",
    )

    /** Brand token -> official registrable domains. */
    val brandDomains: Map<String, Set<String>> = mapOf(
        "kbank" to setOf("kasikornbank.com", "kbank.co.th"),
        "kasikorn" to setOf("kasikornbank.com"),
        "scb" to setOf("scb.co.th"),
        "krungthai" to setOf("krungthai.com"),
        "ktb" to setOf("krungthai.com"),
        "bangkokbank" to setOf("bangkokbank.com"),
        "krungsri" to setOf("krungsri.com"),
        "ttb" to setOf("ttbbank.com"),
        "gsb" to setOf("gsb.or.th"),
        "baac" to setOf("baac.or.th"),
        "thaipost" to setOf("thailandpost.co.th", "thailandpost.com"),
        "thailandpost" to setOf("thailandpost.co.th", "thailandpost.com"),
        "kerry" to setOf("kerryexpress.com"),
        "flashexpress" to setOf("flashexpress.co.th", "flashexpress.com"),
        "lazada" to setOf("lazada.co.th", "lazada.com"),
        "shopee" to setOf("shopee.co.th", "shopee.com"),
        "line" to setOf("line.me", "line.biz", "linecorp.com"),
        "pea" to setOf("pea.co.th"),
        "mea" to setOf("mea.or.th"),
        "egat" to setOf("egat.co.th"),
        "revenue" to setOf("rd.go.th"),
        "dsi" to setOf("dsi.go.th"),
        "police" to setOf("royalthaipolice.go.th", "police.go.th"),
        "sso" to setOf("sso.go.th"),
        "paotang" to setOf("krungthai.com"),
        "promptpay" to setOf("bot.or.th"),
        "dol" to setOf("dol.go.th"),
    )

    // ---------------- Phones ----------------

    /** Verified official short codes / call-centres (digits only) -> organisation. */
    val officialNumbers: Map<String, String> = mapOf(
        "1441" to "ศูนย์ AOC ต่อต้านการฉ้อโกงออนไลน์",
        "1599" to "ตำรวจ (แจ้งเหตุด่วน)",
        "191" to "ตำรวจ (แจ้งเหตุด่วนเหตุร้าย)",
        "1111" to "ศูนย์บริการข้อมูลภาครัฐ",
        "1213" to "ศูนย์คุ้มครองผู้ใช้บริการทางการเงิน ธปท.",
        "1202" to "กรมสอบสวนคดีพิเศษ (DSI)",
        "1155" to "ตำรวจท่องเที่ยว",
        "1669" to "การแพทย์ฉุกเฉิน",
        "1129" to "การไฟฟ้าส่วนภูมิภาค (PEA)",
        "1130" to "การไฟฟ้านครหลวง (MEA)",
        "1506" to "สำนักงานประกันสังคม",
        "1161" to "กรมสรรพากร",
        "1545" to "ไปรษณีย์ไทย",
        "1333" to "ธนาคารกรุงเทพ",
        "1572" to "ธนาคารกรุงศรีอยุธยา",
        "1428" to "ธนาคารทีทีบี",
        "1115" to "ธนาคารออมสิน",
        "028888888" to "ธนาคารกสิกรไทย",
        "027777777" to "ธนาคารไทยพาณิชย์",
        "021111111" to "ธนาคารกรุงไทย",
        "025550555" to "ธ.ก.ส.",
    )

    /** International prefixes widely reported in Thailand as spoofed call-centre numbers. */
    val spoofedPrefixes = listOf("697", "698")
}
