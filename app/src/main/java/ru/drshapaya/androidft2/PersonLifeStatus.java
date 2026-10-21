package ru.drshapaya.androidft2;

/** Manual choices and automatic estimates from birth/death dates. */
final class PersonLifeStatus {
    private static final java.util.regex.Pattern YEAR=java.util.regex.Pattern.compile("(?<!\\d)(\\d{4})(?!\\d)");
    private static final java.util.regex.Pattern ISO=java.util.regex.Pattern.compile("^(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})$");
    private static final java.util.regex.Pattern DMY=java.util.regex.Pattern.compile("^(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{4})$");
    private static volatile int[] today;
    private static volatile long nextRefresh;
    static final String UNKNOWN = "unknown";
    static final String LIVING = "living";
    static final String DECEASED = "deceased";

    private PersonLifeStatus() {}

    static String normalize(String value) {
        return LIVING.equals(value) || DECEASED.equals(value) ? value : UNKNOWN;
    }

    static boolean hasDeathDate(Person person) {
        return person != null && (present(person.died) || present(person.diedDay)
            || present(person.diedMonth) || present(person.diedYear));
    }

    static String resolve(Person person) {
        if (person == null) return UNKNOWN;
        if(hasDeathDate(person)) return DECEASED;
        String manual=normalize(person.lifeStatus);
        if(!UNKNOWN.equals(manual)) return manual;
        long now=System.currentTimeMillis();
        if(today==null || now>=nextRefresh) refreshToday(now);
        int[] date=today;
        return automatic(person,date[0],date[1],date[2]);
    }

    static String resolve(Person person,java.util.Calendar now) {
        if(person==null) return UNKNOWN;
        if(hasDeathDate(person)) return DECEASED;
        String manual=normalize(person.lifeStatus);
        if(!UNKNOWN.equals(manual)) return manual;
        return automatic(person,now.get(java.util.Calendar.YEAR),now.get(java.util.Calendar.MONTH)+1,now.get(java.util.Calendar.DAY_OF_MONTH));
    }

    private static synchronized void refreshToday(long now) {
        if(today!=null && now<nextRefresh) return;
        java.util.Calendar date=java.util.Calendar.getInstance();
        today=new int[]{date.get(java.util.Calendar.YEAR),date.get(java.util.Calendar.MONTH)+1,date.get(java.util.Calendar.DAY_OF_MONTH)};
        nextRefresh=now+60_000;
    }

    private static String automatic(Person person,int year,int month,int day) {
        int bornYear=number(person.bornYear),bornMonth=number(person.bornMonth),bornDay=number(person.bornDay);
        if(bornYear<=0 && present(person.born)) {
            java.util.regex.Matcher match=YEAR.matcher(person.born); if(match.find()) bornYear=number(match.group(1));
            java.util.regex.Matcher iso=ISO.matcher(person.born.trim()),dmy=DMY.matcher(person.born.trim());
            if(iso.matches()) { bornMonth=number(iso.group(2)); bornDay=number(iso.group(3)); }
            else if(dmy.matches()) { bornMonth=number(dmy.group(2)); bornDay=number(dmy.group(1)); }
        }
        if(bornYear<=0) return (bornMonth>=1 && bornMonth<=12) || (bornDay>=1 && bornDay<=31) ? LIVING : UNKNOWN;
        if(bornYear>year) return UNKNOWN;
        int age=year-bornYear;
        if(bornMonth>month || (bornMonth==month && bornDay>day)) age--;
        if(age<0) return UNKNOWN;
        return age>120 ? DECEASED : age>100 ? UNKNOWN : LIVING;
    }

    private static int number(String value) {
        try { return Integer.parseInt(value==null ? "" : value.trim()); }catch(NumberFormatException ignored) { return -1; }
    }

    static String label(Person person) {
        return label(resolve(person));
    }

    static String label(String status) {
        return LIVING.equals(status) ? "Жив" : DECEASED.equals(status) ? "Умер" : "Неизвестно";
    }

    private static boolean present(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
