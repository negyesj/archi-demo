# Telepítés Google Cloud Runra (demó)

Google Cloud Shellből, a repó `main` branchének gyökeréből futtatandó szkriptek, sorrendben:

| Lépés | Szkript | Mit csinál | Idő |
|---|---|---|---|
| 1 | `00-setup.sh` | API-k, Artifact Registry, VPC + alhálózatok, tűzfal, Cloud NAT, fix belső IP, szolgáltatásfiókok | 3–5 perc |
| 2 | `01-secrets.sh` | véletlen jelszavak és kulcsok a Secret Managerben | 1 perc |
| 3 | `02-infra-vm.sh` | `archi-infra` VM: PostgreSQL 16, MongoDB 7, Kafka (KRaft), Redis 7 Docker Compose-zal | 5–8 perc |
| 4 | `03-build.sh` | branchek összerakása, 13 image buildje Cloud Builddel | 20–30 perc |
| 5 | `04-deploy.sh` | Keycloak, 6 backend, 2 gateway, 3 worker pool, 2 frontend Cloud Runra | 10–15 perc |
| 6 | `05-smoke.sh` | füstteszt: tenant létrehozása és provisioningja a felhőben | 2–5 perc |
| – | `98-pause.sh pause\|resume` | szüneteltetés két kipróbálás között | |
| – | `99-teardown.sh` | minden erőforrás törlése | |

Beállítások: `config.env` (projekt: `archi-demo-512819`, régió: `europe-west1`, zóna: `europe-west1-b`). Bármelyik felülírható
környezeti változóval, pl. `REGION=europe-west1 ./00-setup.sh`.

```bash
git clone https://github.com/negyesj/archi-demo.git && cd archi-demo/deploy/gcp
./00-setup.sh && ./01-secrets.sh && ./02-infra-vm.sh && ./03-build.sh && ./04-deploy.sh && ./05-smoke.sh
```

Ez demó topológia: az adatbázisok és a Kafka egyetlen VM-en futnak, a Cloud Run szolgáltatások Direct VPC
egressen érik el őket. Éles környezethez: Cloud SQL, Managed Service for Apache Kafka, MongoDB Atlas,
Memorystore, belső ingress a backend szolgáltatásokon (lásd a lépésről lépésre útmutatót).
