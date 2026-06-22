package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistInfoRepositoryTest {
    @Test
    fun loadsMoegirlArtistInfoAfterOtherDomesticSourcesMiss() {
        val urls = ArrayList<String>()
        val repository = ArtistInfoRepository(
            textFetcher = { url ->
                urls += url
                when {
                    url.contains("music.163.com") -> "{}"
                    url.contains("baike.baidu.com/api/openapi/BaikeLemmaCardApi") -> """{"errno":2}"""
                    url.contains("baike.baidu.com/item/") -> "创建词条"
                    url.contains("zh.moegirl.org.cn/api.php") && url.contains("list=search") ->
                        """{"query":{"search":[{"title":"Echo Unit"}]}}"""
                    url.contains("zh.moegirl.org.cn/api.php") && url.contains("prop=extracts") ->
                        """{"query":{"pages":{"1":{"title":"Echo Unit","extract":"Echo Unit 是萌娘百科中的测试音乐组合条目，常用于艺人资料测试。"}}}}"""
                    else -> error("Unexpected URL $url")
                }
            }
        )

        val info = repository.loadArtistInfo("Echo Unit")

        assertEquals("Echo Unit", info?.artist)
        assertEquals("萌娘百科", info?.source)
        assertTrue(info?.summary.orEmpty().contains("测试音乐组合"))
        assertTrue(urls.any { it.contains("zh.moegirl.org.cn/api.php") })
        assertTrue(urls.none { it.contains("wikipedia.org") })
    }

    @Test
    fun loadsBaiduBaikeLemmaSummaryWhenCardApiHasNoAbstract() {
        val repository = ArtistInfoRepository(
            textFetcher = { url ->
                when {
                    url.contains("music.163.com") -> "{}"
                    url.contains("baike.baidu.com/api/openapi/BaikeLemmaCardApi") -> """{"errno":2}"""
                    url.contains("baike.baidu.com/item/") -> """
                        <html>
                          <head>
                            <title>周杰伦（华语流行乐男歌手、音乐人、演员、导演）_百度百科</title>
                          </head>
                          <body>
                            <div class="lemmaSummary">
                              <div class="para">周杰伦（Jay Chou），华语流行乐男歌手、音乐人、演员、导演。</div>
                            </div>
                          </body>
                        </html>
                    """.trimIndent()
                    else -> error("Unexpected URL $url")
                }
            }
        )

        val info = repository.loadArtistInfo("周杰伦")

        assertEquals("百度百科", info?.source)
        assertTrue(info?.artist.orEmpty().contains("周杰伦"))
        assertTrue(info?.summary.orEmpty().contains("华语流行乐男歌手"))
    }
}
