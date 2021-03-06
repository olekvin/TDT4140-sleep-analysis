package tdt4140.gr1816.app.api;

import static com.mongodb.client.model.Filters.eq;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.DeleteResult;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import tdt4140.gr1816.app.api.types.SleepData;
import tdt4140.gr1816.app.api.types.User;

public class SleepDataRepository {

  private final MongoCollection<Document> sleepsDoc;

  public SleepDataRepository(MongoCollection<Document> sleepsDoc) {
    this.sleepsDoc = sleepsDoc;
  }

  public SleepData findById(String id) {
    Document doc = sleepsDoc.find(eq("_id", new ObjectId(id))).first();
    return sleepData(doc);
  }

  public List<SleepData> getAllSleepData(String userId) {
    List<SleepData> allSleepData = new ArrayList<>();
    for (Document doc : sleepsDoc.find(eq("user", userId))) {
      allSleepData.add(sleepData(doc));
    }
    return allSleepData;
  }

  /*
   * This method assumes that the dates are of the correct form 'yyyy-mm-dd'
   */
  public List<SleepData> getSleepDataBetweenDates(String userId, String startDate, String endDate) {
    return getAllSleepData(userId)
        .stream()
        .filter(
            data -> {
              DateTimeFormatter dmf = DateTimeFormatter.ISO_LOCAL_DATE;
              LocalDate from = LocalDate.parse(startDate, dmf);
              LocalDate to = LocalDate.parse(endDate, dmf);
              LocalDate date = LocalDate.parse(data.getDate(), dmf);
              return date.compareTo(from) >= 0 && date.compareTo(to) <= 0;
            })
        .collect(Collectors.toList());
  }

  public SleepData saveSleepData(SleepData sleepData) {

    Document doc = new Document();
    doc.append("user", sleepData.getUserId());
    doc.append("date", sleepData.getDate());
    doc.append("duration", sleepData.getDuration());
    doc.append("efficiency", sleepData.getEfficiency());
    Bson filter =
        Filters.and(
            Filters.eq("date", sleepData.getDate()), Filters.eq("user", sleepData.getUserId()));
    Document old = sleepsDoc.find(filter).first();
    if (old != null) {
      sleepsDoc.updateOne(
          eq("_id", new ObjectId(old.get("_id").toString())), new Document("$set", doc));
      doc.append("_id", old.get("_id").toString());
    } else {
      sleepsDoc.insertOne(doc);
    }
    return new SleepData(
        doc.get("_id").toString(),
        sleepData.getUserId(),
        sleepData.getDate(),
        sleepData.getDuration(),
        sleepData.getEfficiency());
  }

  public boolean deleteSleepData(SleepData sleepData) {
    Document doc = sleepsDoc.find(eq("_id", new ObjectId(sleepData.getId()))).first();
    DeleteResult result = sleepsDoc.deleteOne(doc);
    return result.wasAcknowledged();
  }

  public SleepData getAverageForGroup(List<User> users, String fromDate, String toDate) {
    double avgEff =
        users
            .stream()
            .map(user -> getSleepDataBetweenDates(user.getId(), fromDate, toDate))
            .mapToDouble(
                data -> data.stream().mapToInt(el -> el.getEfficiency()).average().orElse(0))
            .average()
            .orElse(0);
    double avgDur =
        users
            .stream()
            .map(user -> getSleepDataBetweenDates(user.getId(), fromDate, toDate))
            .mapToDouble(data -> data.stream().mapToInt(el -> el.getDuration()).average().orElse(0))
            .average()
            .orElse(0);
    return new SleepData(null, null, null, (int) avgDur, (int) avgEff);
  }

  private SleepData sleepData(Document doc) {
    if (doc == null) {
      return null;
    }
    return new SleepData(
        doc.get("_id").toString(),
        doc.getString("user"),
        doc.getString("date"),
        doc.getInteger("duration"),
        doc.getInteger("efficiency"));
  }
}
