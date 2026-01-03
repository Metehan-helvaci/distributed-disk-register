# HaToKuSe – Hata Toleranslı Dağıtık Mesaj Saklama Sistemi
Sistem Programlama Dersi Ödevi  
Java + TCP + gRPC + Protobuf

---

## 1. Bu projede ne yaptık?

Bu projede, istemciden gelen mesajları **tek bir sunucuya bağlı kalmadan**, birden fazla sunucuya dağıtarak saklayan, **hata toleranslı** bir mesaj saklama sistemi geliştirdik.

Sistemde:
- İstemci yalnızca **lider sunucu** ile konuşur
- Lider, mesajı kendi diskine kaydeder
- Aynı mesajı belirlenen **hata toleransı (tolerance)** kadar aile üyesine (member) dağıtır
- Bir veya daha fazla üye çökse bile (crash), mesaj sistemden kaybolmaz

İstemci ile lider arasındaki iletişim **text tabanlı**,  
lider ile üyeler arasındaki iletişim ise **gRPC + Protobuf** ile yapılmıştır.

---

## 2. 1. Aşama – Proje organizasyonu ve GitHub süreci

İlk olarak:
- Hocanın verdiği **şablon repository** fork edildi
- GitHub Projects altında bir **proje panosu** oluşturuldu
- Ödev grup üyeleri arasında, küçük iş parçalarına (task) bölündü

Her aşama için:
- Ayrı task açıldı
- Geliştirme tamamlandıkça commit atıldı
- Takım çalışması merge işlemleri ile ilerletildi

Projenin her iş parçasında tüm grup üyelerinin emeği oldu. Burada amacımız sadece kod yazmak değil, süreci yönetmekti.

---

## 3. 2. Aşama – TCP Server ve Komut Ayrıştırma

Bu aşamada istemcinin liderle nasıl konuşacağını ele aldık.

İstemci sadece iki komut gönderebilmektedir:

SET <message_id> <message>
GET <message_id>


Lider tarafında:
- TCP socket açıldı
- İstemciden gelen her satır okundu
- Gelen satır bir **Command Parser** ile ayrıştırıldı

Komutlar ikiye ayrıldı:
- SET komutu → mesaj ekleme
- GET komutu → mesaj okuma

Bu sayede gelen text mesajlar doğrudan işlenebilir hale getirildi.

---

## 4. 3. Aşama – Mesajları Diskte Saklama (Tek Node)

Bu aşamada mesajları sadece RAM’de tutmanın yeterli olmadığını ele aldık.

Her mesaj:
- `messages/` klasörü altında
- <img width="424" height="254" alt="Ekran Resmi 2026-01-03 13 22 48" src="https://github.com/user-attachments/assets/fb49f1f9-9f8f-4579-8e12-03555450a49e" />

- **ayrı bir dosya** olarak saklandı

Örnek:

messages/42.msg


### SET işlemi:
- Dosya oluşturulur (veya üzerine yazılır)
- İçine sadece mesaj metni yazılır

### GET işlemi:
- Dosya diskten okunur
- İçeriği istemciye geri gönderilir

Bu aşamada sistem henüz tek sunucuda çalışmaktadır ancak **kalıcılık** sağlanmıştır.

---<img width="582" height="96" alt="Ekran Resmi 2026-01-03 13 32 50" src="https://github.com/user-attachments/assets/e2c94172-b18b-4512-9dc7-4149d8f37df8" />


## 5. Buffered ve Unbuffered IO farkı

Bu aşamada dosyaya yazma ve okuma için iki farklı yöntem incelendi.

### Buffered IO
- Daha az sistem çağrısı yapar
- Büyük veri ve sık IO işlemleri için daha verimlidir

### Unbuffered IO
- Daha düşük seviyelidir
- Küçük ve anlık yazmalar için uygundur
- Zero-copy yaklaşımına daha yakındır

Projede zaman kaybetmemek için öncelikle **UnBuffered IO** kullanıldı,  
ancak farklar bu README dosyasında açıklanmıştır.

---

## 6. 4. Aşama – gRPC ve Protobuf ile Üyeler Arası Haberleşme

Bu aşamada lider ile aile üyeleri arasındaki iletişimi text yerine **Protobuf** ile modelledik.

Mesaj artık iki parçalıdır:
- message_id
- message_text

Bunlar `StoredMessage` adlı Protobuf nesnesi içinde tutulmaktadır.

Lider:
- gRPC üzerinden `Store` çağrısı ile üyeye mesaj gönderir
- `Retrieve` çağrısı ile üyeden mesaj ister

Bu aşamada lider ve üye aynı process içinde çalıştırılabilir, amaç sadece **gRPC altyapısını ayağa kaldırmaktır**.

---

## 7. 5. Aşama – Hata Toleransı 1 ve 2 ile Dağıtık Kayıt

Bu aşamada sistem gerçekten **dağıtık** hale getirildi.

`tolerance.conf` dosyasından okunan değere göre:

TOLERANCE=2


### SET isteğinde lider:
1. Mesajı kendi diskine kaydeder
2. Tolerance sayısı kadar üye seçer
3. Bu üyelere gRPC ile mesajı gönderir
4. Tüm üyeler başarılıysa istemciye `OK` döner

Lider, her mesaj için şu bilgiyi tutar:
- Bu mesaj hangi üyelerde saklanıyor?
- Bu mesajları Mapin içinde tuttuk böylelikle hangi mesaj hangi node içinde belli oldu.
---

## 8. 6. Aşama – Genel Hâliyle Tolerance = n ve Yük Dağılımı

Bu aşamada sistem:
- Tolerance = 1, 2, 3, …, 7 olacak şekilde genelleştirildi
- Üye sayısı dinamik hale getirildi

Mesajlar:
- round-robin
- veya message_id bazlı

şekilde üyelere dağıtıldı.

Amaç:
- Uzun vadede üyelerin disk yüklerinin birbirine yakın olmasıdır

Testlerde:
- 1000 SET sonrası üyelerde yaklaşık eşit dağılım gözlemlenmiştir
- Bu resimde tolerance değeri 2 olduğu için her bir mesaj 2 üyede saklanmıştır.
<img width="368" height="99" alt="Ekran Resmi 2026-01-03 13 27 23" src="https://github.com/user-attachments/assets/c9ab30ff-236f-43cb-a473-94751a4cbffa" />
- Bu resimde Toplam kaydedilen mesaj sayısını gösterir
<img width="246" height="56" alt="Ekran Resmi 2026-01-03 13 27 46" src="https://github.com/user-attachments/assets/6dcb00bb-f2f9-446d-8482-ab9363b70725" />



## 9. 7. Aşama – Crash Senaryoları ve Recovery

Bu aşamada sistemin gerçekten **hata toleranslı** olup olmadığı test edildi.

Üyelerden biri manuel olarak kapatıldığında:
- Lider, GET sırasında hata alır
- O üyeyi “dead” olarak işaretler
- Diğer üyelere yönelir

### Test sonucu:
- Tolerance değeri kadar üye hayatta kaldığı sürece
- Mesaj sistemden kaybolmaz
- İstemciye başarıyla geri döner

Bu durum log çıktıları ile doğrulanmıştır.

---

## 10. Dinamik Üyelik

Sisteme sonradan katılan üyeler:
- Eski mesajları almak zorunda değildir
- Yeni gelen mesajlarda zamanla daha fazla yük alarak sistemi dengeler

Bu yapı gerçek dağıtık sistem davranışına uygundur.

---

## 11. Sonuç

Bu projede:
- Dağıtık
- Disk tabanlı
- Hata toleranslı
- Yük dengeli
- Crash senaryolarına dayanıklı

bir **HaToKuSe (Hata-Tolere Kuyruk Servisi)** başarıyla geliştirilmiştir.

---

## 12. Çalıştırma

```bash
mvn clean compile
mvn exec:java "-Dexec.mainClass=com.example.family.NodeMain"


Lider ve üyeler farklı terminallerden başlatılarak test edilebilir.
Teşekkürler.
