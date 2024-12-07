1. Aggiungere un Certificato SSL:
   keytool -genkey -keyalg RSA -keysize 2048 -keystore server.keystore -validity 3650

Informazioni usate:

Password Scelta: Prova1234!
Nome e Cognome: David M
Nome dell'unità organizzativa: Prova
Nome dell'organizzazione: David
Località: Italia
Provincia: Italia
Due lettere del paese dell'unità: IT

Informazioni bonus: Il Certificato è autofirmato con validità di 3650 giorni.

2. Per vedere le informazioni della keystore:
   -list -v -keystore server.keystore -storepass [Password]

3. Per Esportare la keystore in crt per poter configurare il client:
   keytool -export -alias mykey -file server.crt -keystore server.keystore -storetype PKCS12

4. Ora che hai creato Il certificato del server, devi farlo approvare al client attraverso un truststore:
   keytool -import -alias server -file server.crt -keystore client.truststore -storetype PKCS12
