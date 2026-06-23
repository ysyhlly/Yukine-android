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

    @Test
    fun triesNextNeteaseArtistCandidateWhenFirstMatchHasNoIntro() {
        val urls = ArrayList<String>()
        val repository = ArtistInfoRepository(
            textFetcher = { url ->
                urls += url
                when {
                    url.contains("api/cloudsearch/pc") -> """
                        {
                          "result": {
                            "artists": [
                              {"id":17679,"name":"しほ","alias":["shiho"]},
                              {"id":54970759,"name":"にほしか","alias":["しほ"]}
                            ]
                          }
                        }
                    """.trimIndent()
                    url.contains("artist/head/info/get?id=17679") -> """
                        {"code":200,"data":{"artist":{"id":17679,"name":"しほ","briefDesc":""}}}
                    """.trimIndent()
                    url.contains("artist/introduction?id=17679") -> """
                        {"code":200,"introduction":[],"briefDesc":"","count":0}
                    """.trimIndent()
                    url.contains("artist/head/info/get?id=54970759") -> """
                        {"code":200,"data":{"artist":{"id":54970759,"name":"にほしか","briefDesc":"にほしか，日本歌手，发布有云音乐热门歌曲。"}}}
                    """.trimIndent()
                    url.contains("artist/introduction?id=54970759") -> """
                        {"code":200,"introduction":[],"briefDesc":"","count":0}
                    """.trimIndent()
                    else -> error("Unexpected URL $url")
                }
            }
        )

        val info = repository.loadArtistInfo("しほ")

        assertEquals("网易云音乐", info?.source)
        assertEquals("にほしか", info?.artist)
        assertTrue(info?.summary.orEmpty().contains("日本歌手"))
        assertTrue(urls.any { it.contains("artist/head/info/get?id=54970759") })
    }
}
